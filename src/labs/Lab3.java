package labs;

import mpi.MPI;
import mpi.Request;
import mpi.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Lab3 {
    static final int ODD_TAG = 1;
    static final int EVEN_TAG = 0;
    static final int FINAL_PROCESS = 0;
    static final int LENGTH = 10;
    static final int MIN = 10;
    static final int MAX = 100;

    public static void main(String[] args){
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        System.out.println("======Start process " + rank + " ======");

        long startTime = System.currentTimeMillis();

        switch (rank) {
            case 1 -> filterAndSort(false);
            case 2 -> filterAndSort(true);
            case 0 -> finalSort();
            default -> sendNumbers(LENGTH, MIN, MAX);
        }

        if (rank <= 2) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.println("Process " + rank + " execution time: " + duration + " ms");
        }

        System.out.println("======Finish process " + rank + " ======");
        MPI.Finalize();
    }

    static void sendNumbers(int length, int min, int max){
        int[] numbers = generateRandomArray(length, min, max);

        List<Integer> evenNumbers = new ArrayList<>();
        List<Integer> oddNumbers = new ArrayList<>();

        for (int number : numbers) {
            if (number % 2 == 0) {
                evenNumbers.add(number);
            } else {
                oddNumbers.add(number);
            }
        }

        int[] evenArray = evenNumbers.stream().mapToInt(i -> i).toArray();
        int[] oddArray = oddNumbers.stream().mapToInt(i -> i).toArray();

        System.out.println("Process " + MPI.COMM_WORLD.Rank() + " generated numbers: " + Arrays.toString(numbers));

        Request[] sendRequests = new Request[2];
        sendRequests[0] = MPI.COMM_WORLD.Isend(evenArray, 0, evenArray.length, MPI.INT, 2, EVEN_TAG);
        System.out.println("Process " + MPI.COMM_WORLD.Rank() + " sending " + evenArray.length + " even numbers to process 2");

        sendRequests[1] = MPI.COMM_WORLD.Isend(oddArray, 0, oddArray.length, MPI.INT, 1, ODD_TAG);
        System.out.println("Process " + MPI.COMM_WORLD.Rank() + " sending " + oddArray.length + " odd numbers to process 1");

        Request.Waitall(sendRequests);
        System.out.println("Process " + MPI.COMM_WORLD.Rank() + " finished sending");
    }

    static void filterAndSort(boolean isEven){
        int size = MPI.COMM_WORLD.Size();
        List<Integer> buffer = new ArrayList<>();

        int receivedCount = 0;
        int expectedCount = size - 3;
        int targetTag = isEven ? EVEN_TAG : ODD_TAG;
        int refusedTag = isEven ? ODD_TAG : EVEN_TAG;
        String processType = isEven ? "Even" : "Odd";

        System.out.println(processType + " process expecting " + expectedCount + " messages");

        while (receivedCount < expectedCount) {
            receivedCount = processMessages(targetTag, buffer, true, processType, receivedCount);
            receivedCount = processMessages(refusedTag, null, false, processType, receivedCount);
        }


        int[] sortedArray = sort(buffer.stream().mapToInt(i -> i).toArray());
        System.out.println(processType + " process sorted array: " + Arrays.toString(sortedArray));

        Request sendRequest = MPI.COMM_WORLD.Isend(sortedArray, 0, sortedArray.length, MPI.INT, FINAL_PROCESS, targetTag);
        sendRequest.Wait();

        System.out.println(processType + " process finished sending sorted array to process " + FINAL_PROCESS);
    }

    private static int processMessages(int tag, List<Integer> buffer, boolean accept, String processType, int currentCount) {
        Status status = MPI.COMM_WORLD.Iprobe(MPI.ANY_SOURCE, tag);
        if (status != null) {
            int source = status.source;
            int count = status.Get_count(MPI.INT);
            int[] message = new int[count];
            MPI.COMM_WORLD.Recv(message, 0, count, MPI.INT, source, tag);

            if (accept && buffer != null) {
                for (int number : message) {
                    buffer.add(number);
                }
                System.out.println(processType + " process received " + message.length + " numbers from process " + source + ": " + Arrays.toString(message));
            } else {
                String refusedType = (tag == EVEN_TAG) ? "even" : "odd";
                System.out.println(processType + " process refused " + message.length + " " + refusedType + " numbers from process " + source);
            }
            return currentCount + 1;
        }
        return currentCount;
    }

    static void finalSort(){
        List<Integer> allNumbers = new ArrayList<>();

        receiveAndMergeArray(1, ODD_TAG, "odd", allNumbers);
        receiveAndMergeArray(2, EVEN_TAG, "even", allNumbers);

        int[] finalArray = sort(allNumbers.stream().mapToInt(i -> i).toArray());
        System.out.println("Process " + FINAL_PROCESS + " final sorted array: " + Arrays.toString(finalArray));
    }

    private static void receiveAndMergeArray(int source, int tag, String arrayType, List<Integer> targetList) {
        Status status = MPI.COMM_WORLD.Probe(source, tag);
        if (status != null) {
            int count = status.Get_count(MPI.INT);
            int[] array = new int[count];
            MPI.COMM_WORLD.Recv(array, 0, count, MPI.INT, source, tag);

            for (int num : array) {
                targetList.add(num);
            }
            System.out.println("Process " + FINAL_PROCESS + " received " + arrayType + " array from process " + source + ": " + Arrays.toString(array));
        }
    }

    static int[] sort(int[] array){
        for (int i = 0; i < array.length - 1; i++) {
            for (int j = 0; j < array.length - i - 1; j++) {
                if (array[j] > array[j + 1]) {
                    int temp = array[j];
                    array[j] = array[j + 1];
                    array[j + 1] = temp;
                }
            }
        }
        return array;
    }

    static int[] generateRandomArray(int length, int min, int max){
        return new java.util.Random()
                .ints(length, min, max)
                .toArray();
    }
}