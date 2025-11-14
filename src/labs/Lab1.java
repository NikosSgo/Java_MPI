package labs;

import mpi.MPI;

public class Lab1 {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        int[] message = new int[1];
        int TAG = 0;

        if ((rank % 2) == 0) {
            if ((rank + 1) != size) {
                message[0] = rank;
                MPI.COMM_WORLD.Send(message, 0, 1, MPI.INT, rank + 1, TAG);
            }
        }
        else {
            MPI.COMM_WORLD.Recv(message, 0, 1, MPI.INT, rank - 1, TAG);
            System.out.println("received: " + message[0]);
        }

        MPI.Finalize();
    }
}