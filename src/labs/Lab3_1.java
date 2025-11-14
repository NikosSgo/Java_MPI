package labs;

import mpi.MPI;
import mpi.Status;

public class Lab3_1 {
    public static void main(String[] args) {
        int[] data = new int[1];
        int[] buf = {1,3,5};
        data[0] = 2025;
        int TAG = 0;
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        if(rank == 0) {
            MPI.COMM_WORLD.Send(data, 0, 1, MPI.INT, 2, TAG);
        }
        else if(rank == 1) {
            MPI.COMM_WORLD.Send(buf, 0, buf.length, MPI.INT, 2, TAG);
        }
        else if(rank == 2) {
            Status st1 = MPI.COMM_WORLD.Probe(0, TAG);
            int count1 = st1.Get_count(MPI.INT);
            int[] back_buf1 = new int[count1];
            MPI.COMM_WORLD.Recv(back_buf1,0, count1, MPI.INT,0, TAG);
            System.out.print("Rank = 0: ");
            for(int i = 0 ; i < count1 ; i++) {
                System.out.print(back_buf1[i] + " ");
            }

            Status st2 = MPI.COMM_WORLD.Probe(1, TAG);
            int count2 = st2.Get_count(MPI.INT);
            int[] back_buf2 = new int[count2];
            MPI.COMM_WORLD.Recv(back_buf2,0, count2, MPI.INT,1, TAG);
            System.out.print("Rank = 1: ");
            for(int i = 0 ; i < count2 ; i++) {
                System.out.print(back_buf2[i] + " ");
            }
        }
        MPI.Finalize();

    }
}
