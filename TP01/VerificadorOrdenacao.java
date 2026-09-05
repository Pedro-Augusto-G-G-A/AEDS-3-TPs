package TP01;

import java.io.IOException;
import java.io.RandomAccessFile;

// Classe auxiliar só pra testar: confere se o arquivo ficou ordenado,
// sem lápides e sem buracos depois da ordenação externa.
public class VerificadorOrdenacao {
    public static void main(String[] args) throws IOException {
        RandomAccessFile raf = new RandomAccessFile("TP01/livros.bin", "r");

        int ultimoIdCabecalho = raf.readInt();
        System.out.println("Último id no cabeçalho: " + ultimoIdCabecalho);

        int idAnterior = -1;
        int total = 0;
        boolean ordenado = true;
        boolean semLapide = true;

        while (raf.getFilePointer() < raf.length()) {
            int id = raf.readInt();
            int tamanho = raf.readInt();
            long posDados = raf.getFilePointer();
            boolean lapide = raf.readBoolean();

            if (lapide) {
                semLapide = false;
                System.out.println("ACHOU LÁPIDE! id=" + id + " (não deveria existir após ordenar)");
            }

            if (id <= idAnterior) {
                ordenado = false;
                System.out.println("FORA DE ORDEM! id=" + id + " veio depois de " + idAnterior);
            }

            idAnterior = id;
            total++;

            raf.seek(posDados + tamanho);
        }

        raf.close();

        System.out.println("Total de registros no arquivo: " + total);
        System.out.println("Ordenado corretamente: " + ordenado);
        System.out.println("Sem lápides: " + semLapide);
    }
}