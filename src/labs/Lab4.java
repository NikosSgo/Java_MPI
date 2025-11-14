package labs;

import mpi.*;
import java.util.Arrays;
import java.util.Random;

public class Lab4 {
    static Random rand = new Random();
    private static final int MASTER = 0;
    private static final int VECTOR_TAG = 1;
    private static final int MATRIX_TAG = 2;
    private static final int RESULT_TAG = 3;

    private static final int MIN = 0;
    private static final int MAX = 10;

    private static final int LEVEL_ERROR = 0;
    private static final int LEVEL_TIME = 1;
    private static final int LEVEL_MAIN = 2;
    private static final int LEVEL_DEBUG = 3;

    private static final int CURRENT_LOG_LEVEL = LEVEL_TIME;

    public static void main(String[] args) {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] vectorSizes = {100,1000,10000};

        for (int vectorSize : vectorSizes) {
            int[] vector = null;
            int[][] matrix = null;

            if (rank == MASTER) {
                vector = randomVector(vectorSize, MIN, MAX);
                matrix = randomMatrix(vectorSize, MIN, MAX);
                logHeader("Processing size: " + vectorSize);
                logMatrix("Initial matrix", matrix);
                logVector("Initial vector", vector);
            }

            // Sequential
            long sequentialTime = 0;
            if (rank == MASTER) {
                logSection("Sequential Multiplication");
                long start = System.currentTimeMillis();
                int[] result = sequentialMultiply(vector, matrix);
                sequentialTime = System.currentTimeMillis() - start;
                log(LEVEL_MAIN, "Sequential time: " + sequentialTime + " ms");
                logVector("Sequential result", result);
            }

            MPI.COMM_WORLD.Barrier();

            // Blocking
            if (rank == MASTER)
                logSection("Parallel (Blocking) Multiplication");

            long blockingStart = System.currentTimeMillis();
            int[] blockingResult = parallelMultiply(vector, matrix, vectorSize);
            long blockingTime = System.currentTimeMillis() - blockingStart;

            if (rank == MASTER) {
                log(LEVEL_MAIN, "Parallel (blocking) time: " + blockingTime + " ms");
                boolean match = Arrays.equals(sequentialMultiply(vector, matrix), blockingResult);
                log(LEVEL_MAIN, "Validation: " + (match ? "PASS ✅" : "FAIL ❌"));
            }

            MPI.COMM_WORLD.Barrier();

            // Non-blocking
            if (rank == MASTER)
                logSection("Parallel (Non-Blocking) Multiplication");

            long nbStart = System.currentTimeMillis();
            int[] nbResult = parallelMultiplyNonBlocking(vector, matrix, vectorSize);
            long nbTime = System.currentTimeMillis() - nbStart;

            if (rank == MASTER) {
                log(LEVEL_MAIN, "Parallel (non-blocking) time: " + nbTime + " ms");
                boolean match = Arrays.equals(sequentialMultiply(vector, matrix), nbResult);
                log(LEVEL_MAIN, "Validation: " + (match ? "PASS ✅" : "FAIL ❌"));

                logFooter("Performance Summary",
                        String.format("Sequential: %d ms", sequentialTime),
                        String.format("Blocking:   %d ms", blockingTime),
                        String.format("Non-block:  %d ms", nbTime)
                );
            }

            MPI.COMM_WORLD.Barrier();
        }

        MPI.Finalize();
    }

    // ======================= Blocking =======================
    static int[] parallelMultiply(int[] vector, int[][] matrix, int vectorSize) {
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        if (rank == MASTER) {
            int baseCols = vectorSize / size;
            int remainder = vectorSize % size;
            int currentCol = 0;
            int[][] partialResults = new int[size][];

            for (int process = 0; process < size; process++) {
                int cols = baseCols + (process < remainder ? 1 : 0);
                int[] vPart = Arrays.copyOfRange(vector, currentCol, currentCol + cols);
                int[] flat = new int[vectorSize * cols];
                int k = 0;
                for (int i = 0; i < vectorSize; i++)
                    for (int j = currentCol; j < currentCol + cols; j++)
                        flat[k++] = matrix[i][j];

                if (process == MASTER) {
                    logDebug("Master computing local part (cols=" + cols + ")");
                    partialResults[MASTER] = sequentialMultiply(vPart, fromArrayToMatrix(flat, vectorSize, cols));
                    logVector("Master partial result", partialResults[MASTER]);
                } else {
                    logVector("Sending vector part to process " + process + ": ",vPart);
                    MPI.COMM_WORLD.Send(vPart, 0, vPart.length, MPI.INT, process, VECTOR_TAG);
                    MPI.COMM_WORLD.Send(flat, 0, flat.length, MPI.INT, process, MATRIX_TAG);
                }
                currentCol += cols;
            }

            for (int process = 1; process < size; process++) {
                Status st = MPI.COMM_WORLD.Probe(process, RESULT_TAG);
                int len = st.Get_count(MPI.INT);
                int[] buf = new int[len];
                MPI.COMM_WORLD.Recv(buf, 0, len, MPI.INT, process, RESULT_TAG);
                partialResults[process] = buf;
                logVector("Received result from process " + process + ": ",buf);
            }

            return mergeResults(partialResults, vectorSize);

        } else {
            logDebug("Slave " + rank + " waiting for data...");

            Status vStatus = MPI.COMM_WORLD.Probe(MASTER, VECTOR_TAG);
            int vLen = vStatus.Get_count(MPI.INT);
            int[] vec = new int[vLen];
            MPI.COMM_WORLD.Recv(vec, 0, vLen, MPI.INT, MASTER, VECTOR_TAG);

            Status mStatus = MPI.COMM_WORLD.Probe(MASTER, MATRIX_TAG);
            int mLen = mStatus.Get_count(MPI.INT);
            int[] flat = new int[mLen];
            MPI.COMM_WORLD.Recv(flat, 0, mLen, MPI.INT, MASTER, MATRIX_TAG);

            logVector("Slave " + rank + " received vector", vec);
            logMatrix("Slave " + rank + " received matrix", fromArrayToMatrix(flat, mLen / vLen, vLen));

            int[] res = sequentialMultiply(vec, fromArrayToMatrix(flat, mLen / vLen, vLen));
            logVector("Slave " + rank + " result", res);
            MPI.COMM_WORLD.Send(res, 0, res.length, MPI.INT, MASTER, RESULT_TAG);
            return null;
        }
    }

    // ======================= Non-Blocking =======================
    static int[] parallelMultiplyNonBlocking(int[] vector, int[][] matrix, int vectorSize) {
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        if (rank == MASTER) {
            int baseCols = vectorSize / size;
            int remainder = vectorSize % size;
            int currentCol = 0;
            int[][] partialResults = new int[size][];
            Request[] reqs = new Request[size * 2];
            int reqIndex = 0;

            for (int process = 0; process < size; process++) {
                int cols = baseCols + (process < remainder ? 1 : 0);
                int[] vPart = Arrays.copyOfRange(vector, currentCol, currentCol + cols);
                int[] flat = new int[vectorSize * cols];
                int k = 0;
                for (int i = 0; i < vectorSize; i++)
                    for (int j = currentCol; j < currentCol + cols; j++)
                        flat[k++] = matrix[i][j];

                if (process == MASTER) {
                    partialResults[MASTER] = sequentialMultiply(vPart, fromArrayToMatrix(flat, vectorSize, cols));
                    logVector("Master partial (non-blocking)", partialResults[MASTER]);
                } else {
                    logDebug("Asynchronously sending to process " + process);
                    reqs[reqIndex++] = MPI.COMM_WORLD.Isend(vPart, 0, vPart.length, MPI.INT, process, VECTOR_TAG);
                    reqs[reqIndex++] = MPI.COMM_WORLD.Isend(flat, 0, flat.length, MPI.INT, process, MATRIX_TAG);
                }
                currentCol += cols;
            }

            Request.Waitall(reqs);

            Request[] recvReqs = new Request[size - 1];
            int[][] recvBuf = new int[size - 1][];
            for (int p = 1; p < size; p++) {
                Status st = MPI.COMM_WORLD.Probe(p, RESULT_TAG);
                int len = st.Get_count(MPI.INT);
                recvBuf[p - 1] = new int[len];
                recvReqs[p - 1] = MPI.COMM_WORLD.Irecv(recvBuf[p - 1], 0, len, MPI.INT, p, RESULT_TAG);
            }
            Request.Waitall(recvReqs);

            for (int p = 1; p < size; p++) {
                partialResults[p] = recvBuf[p - 1];
                logVector("Received result from process " + p + ": ",recvBuf[p-1]);
            }

            return mergeResults(partialResults, vectorSize);

        } else {
            logDebug("Slave " + rank + " (non-blocking) waiting...");
            Status vSt = MPI.COMM_WORLD.Probe(MASTER, VECTOR_TAG);
            int vLen = vSt.Get_count(MPI.INT);
            Status mSt = MPI.COMM_WORLD.Probe(MASTER, MATRIX_TAG);
            int mLen = mSt.Get_count(MPI.INT);

            int[] vec = new int[vLen];
            int[] flat = new int[mLen];
            Request r1 = MPI.COMM_WORLD.Irecv(vec, 0, vLen, MPI.INT, MASTER, VECTOR_TAG);
            Request r2 = MPI.COMM_WORLD.Irecv(flat, 0, mLen, MPI.INT, MASTER, MATRIX_TAG);
            Request.Waitall(new Request[]{r1, r2});

            int[][] localM = fromArrayToMatrix(flat, mLen / vLen, vLen);
            int[] res = sequentialMultiply(vec, localM);
            logVector("Slave " + rank + " computed async result", res);
            MPI.COMM_WORLD.Isend(res, 0, res.length, MPI.INT, MASTER, RESULT_TAG).Wait();
            return null;
        }
    }

    // ======================= Helpers =======================
    static int[] sequentialMultiply(int[] vector, int[][] matrix) {
        int[] result = new int[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            int sum = 0;
            for (int j = 0; j < vector.length; j++) sum += matrix[i][j] * vector[j];
            result[i] = sum;
        }
        return result;
    }

    static int[] randomVector(int size, int min, int max) {
        return rand.ints(size, min, max).toArray();
    }

    static int[][] randomMatrix(int size, int min, int max) {
        int[][] m = new int[size][size];
        for (int i = 0; i < size; i++) m[i] = randomVector(size, min, max);
        return m;
    }

    static int[][] fromArrayToMatrix(int[] array, int rows, int cols) {
        int[][] m = new int[rows][cols];
        int k = 0;
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                m[i][j] = array[k++];
        return m;
    }

    static int[] mergeResults(int[][] parts, int n) {
        int[] res = new int[n];
        for (int i = 0; i < n; i++)
            for (int p = 0; p < parts.length; p++)
                if (parts[p] != null)
                    res[i] += parts[p][i];
        return res;
    }

    // ======================= Logging =======================
    private static void log(int level, String msg) {
        if (level <= CURRENT_LOG_LEVEL) System.out.println(msg);
    }

    private static void logDebug(String msg) {
        if (LEVEL_DEBUG <= CURRENT_LOG_LEVEL)
            System.out.println("[DEBUG] " + msg);
    }

    private static void logVector(String title, int[] v) {
        if (LEVEL_DEBUG <= CURRENT_LOG_LEVEL){
            System.out.println("[DEBUG] " + title + ":");
            printVector(v);
        }
    }

    private static void logMatrix(String title, int[][] m) {
        if (LEVEL_DEBUG <= CURRENT_LOG_LEVEL) {
            System.out.println("[DEBUG] " + title + ":");
            printMatrix(m);
        }
    }

    private static void logHeader(String title) {
        log(LEVEL_TIME, "\n" + "═".repeat(50) + "\n" +
                title + "\n" +
                "═".repeat(50));
    }

    private static void logSection(String title) {
        log(LEVEL_MAIN, "\n" + "─".repeat(40) + "\n" +
                "🔹 " + title + "\n" +
                "─".repeat(40));
    }

    private static void logFooter(String title, String... lines) {
        log(LEVEL_TIME, "\n" + "═".repeat(50));
        log(LEVEL_TIME, "📊 " + title);
        for (String line : lines)
            log(LEVEL_TIME, "   " + line);
        log(LEVEL_TIME, "═".repeat(50));
    }

    static void printVector(int[] vector) {
        if (vector.length <= 10) {
            System.out.println(Arrays.toString(vector));
        } else {
            System.out.println("Vector[size=" + vector.length + "]: [" +
                    vector[0] + ", " + vector[1] + ", " + vector[2] + " ... " +
                    vector[vector.length - 3] + ", " + vector[vector.length - 2] + ", " +
                    vector[vector.length - 1] + "]");
        }
    }

    static void printMatrix(int[][] matrix) {
        if (matrix.length <= 10) {
            for (int i = 0; i < matrix.length; i++) {
                printVector(matrix[i]);
            }
        } else {
            System.out.println("Matrix[" + matrix.length + "x" + matrix[0].length + "]:");

            // Первые 3 строки
            for (int i = 0; i < 3; i++) {
                System.out.print("  ");
                printVector(matrix[i]);
            }

            // Пропуск
            System.out.println("  ...");

            // Последние 3 строки
            for (int i = matrix.length - 3; i < matrix.length; i++) {
                System.out.print("  ");
                printVector(matrix[i]);
            }
        }
    }
}