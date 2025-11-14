package labs.lab5;

import mpi.MPI;
import java.io.*;
import java.util.*;

public class Lab5 {
    static final int MASTER = 0;
    static final String fileName = "src/labs/lab5/hypercube_12_size.txt";

    public static void main(String[] args) {
        MPI.Init(args);
        long globalStart = System.currentTimeMillis(); // <--- начало общего времени

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int N = readMatrixSize(fileName);

        if (!isPowerOfTwo(N)) {
            if (rank == MASTER)
                System.out.println("Размер матрицы не является степенью двойки!");
            MPI.Finalize();
            return;
        }

        int n = Integer.numberOfTrailingZeros(N); // N = 2^n

        // распределение строк (все процессы, включая 0)
        int[] rows = processRows(rank, size, N);
        int start = rows[0], end = rows[1];

        // --- замер времени работы каждого процесса ---
        long startTime = System.currentTimeMillis();

        int[][] localRows = readMatrix(fileName, start, end);
        boolean localOk = checkRegularity(localRows, n);

        long endTime = System.currentTimeMillis();
        long localTime = endTime - startTime;

        // --- сбор результатов ---
        int[] localFlag = {localOk ? 1 : 0};
        int[] globalFlag = new int[1];
        MPI.COMM_WORLD.Allreduce(localFlag, 0, globalFlag, 0, 1, MPI.INT, MPI.MIN);

        // сбор времени (для анализа)
        long[] times = new long[size];
        MPI.COMM_WORLD.Gather(new long[]{localTime}, 0, 1, MPI.LONG,
                times, 0, 1, MPI.LONG, MASTER);

        if (rank == MASTER) {
            System.out.println("\n=== Время проверки регулярности по процессам ===");
            for (int i = 0; i < size; i++) {
                System.out.printf("Процесс %d: %d мс%n", i, times[i]);
            }
        }

        // если хотя бы один процесс нашёл нарушение — все завершаются
        if (globalFlag[0] == 0) {
            if (rank == MASTER)
                System.out.println("Граф НЕ является гиперкубом (найдена не n-регулярная вершина)");
            long globalEnd = System.currentTimeMillis();
            if (rank == MASTER)
                System.out.println("\nОбщее время выполнения: " + (globalEnd - globalStart) + " мс");
            MPI.Finalize();
            return;
        }

        // --- проверка связности (только MASTER) ---
        if (rank == MASTER) {
            long startConn = System.currentTimeMillis();

            int[][] fullMatrix = readMatrix(fileName, 0, N - 1);
            boolean connected = isConnected(fullMatrix);

            long endConn = System.currentTimeMillis();
            long connTime = endConn - startConn;

            if (connected) {
                System.out.println("\nГраф — гиперкуб Q" + n);
            } else {
                System.out.println("\nГраф НЕ является гиперкубом (граф несвязный)");
            }

            System.out.println("Время проверки связности: " + connTime + " мс");
        }

        // --- финальный замер общего времени ---
        long globalEnd = System.currentTimeMillis();
        if (rank == MASTER) {
            System.out.println("\n==============================");
            System.out.println("Общее время выполнения программы: " +
                    (globalEnd - globalStart) + " мс");
            System.out.println("==============================");
        }

        MPI.Finalize();
    }

    // проверка степени двойки
    static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    // равномерное распределение строк
    static int[] processRows(int rank, int size, int matrixSize) {
        int base = matrixSize / size;
        int remainder = matrixSize % size;

        int start = rank * base + Math.min(rank, remainder);
        int rowsForRank = base + (rank < remainder ? 1 : 0);
        int end = start + rowsForRank - 1;

        if (rowsForRank == 0)
            return new int[]{0, -1};

        System.out.printf("Процесс %d обрабатывает строки %d..%d%n", rank, start, end);
        return new int[]{start, end};
    }

    // чтение только нужных строк матрицы
    static int[][] readMatrix(String filename, int startRow, int endRow) {
        List<int[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                if (lineNum == 0) { // первая строка — размер
                    lineNum++;
                    continue;
                }

                int rowIdx = lineNum - 1;
                if (rowIdx >= startRow && rowIdx <= endRow) {
                    int[] values = Arrays.stream(line.trim().split("\\s+"))
                            .mapToInt(Integer::parseInt)
                            .toArray();
                    rows.add(values);
                }
                lineNum++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rows.toArray(new int[0][]);
    }

    // чтение размера (первой строки)
    static int readMatrixSize(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            return Integer.parseInt(br.readLine().trim());
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // проверка n-регулярности
    static boolean checkRegularity(int[][] part, int n) {
        for (int i = 0; i < part.length; i++) {
            int degree = 0;
            for (int val : part[i])
                if (val != 0) degree++;
            if (degree != n) {
                System.out.printf("Найдено нарушение регулярности: строка имеет степень %d (ожидалось %d)%n",
                        degree, n);
                return false;
            }
        }
        return true;
    }

    // BFS для проверки связности
    static boolean isConnected(int[][] adj) {
        int N = adj.length;
        boolean[] visited = new boolean[N];
        Queue<Integer> q = new LinkedList<>();
        q.add(0);
        visited[0] = true;
        int count = 1;

        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v = 0; v < N; v++) {
                if (adj[u][v] != 0 && !visited[v]) {
                    visited[v] = true;
                    q.add(v);
                    count++;
                }
            }
        }
        return count == N;
    }
}
