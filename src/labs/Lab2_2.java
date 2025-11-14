package labs;

import mpi.MPI;
import mpi.Request;

public class Lab2_2 {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        int[] message = new int[1];
        int TAG = 0;
        Request recvRequest;

        if (size == 1) {
            System.out.println("process: 0; received: 0");
        }
        else {
            if (rank == 0) {
                message[0] = rank;
                MPI.COMM_WORLD.Isend(message, 0, 1, MPI.INT, rank + 1, TAG);
                recvRequest = MPI.COMM_WORLD.Irecv(message, 0, 1, MPI.INT, size - 1, TAG);
                recvRequest.Wait();
                System.out.println("process: " + rank + "; received: " + message[0]);
            }
            else {
                recvRequest = MPI.COMM_WORLD.Irecv(message, 0, 1, MPI.INT, rank - 1, TAG);
                recvRequest.Wait();
                System.out.println("process: " + rank + "; received: " + message[0]);
                message[0] += rank;
                if (rank + 1 != size) {
                    MPI.COMM_WORLD.Isend(message, 0, 1, MPI.INT, rank + 1, TAG);
                }
                else {
                    MPI.COMM_WORLD.Isend(message, 0, 1, MPI.INT, 0, TAG);
                }
            }
        }

        MPI.Finalize();
    }
}
