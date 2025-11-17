package labs.lab5;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class HypercubeGenerator {

    static String FILE_NAME = "src/labs/lab5/hypercube_";
    static int N = 6; // степень гиперкуба

    // Пишем построчно, без хранения всей матрицы
    public static void generateHypercubeToFile(int n, String filename) {
        int size = 1 << n; // количество вершин (2^n)

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(size + "\n"); // первая строка — размер матрицы

            for (int i = 0; i < size; i++) {
                int[] row = new int[size];

                for (int j = 0; j < n; j++) {
                    int neighbor = i ^ (1 << j);
                    row[neighbor] = 1;
                }

                // запишем строку в файл
                for (int k = 0; k < size; k++) {
                    writer.write(row[k] + (k == size - 1 ? "" : " "));
                }
                writer.newLine();

                // каждые 1000 строк покажем прогресс
                if (i % 1000 == 0) {
                    System.out.println("Записано строк: " + i + " / " + size);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String fileName = FILE_NAME + N + "_size" + ".txt";
        System.out.println("Начало генерации гиперкуба размерности " + N);
        generateHypercubeToFile(N, fileName);
        System.out.println("Гиперкуб записан в " + fileName);
    }
}
