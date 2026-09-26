package TP02;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GeradorBin {
    public static void main(String[] args) {
        BufferedReader br;
        RandomAccessFile rafLivros;
        File arqArvore;
        ArvoreBMais arvore;

        try {
            // ---------- entrada: ordem da árvore ----------
            Scanner sc = new Scanner(System.in);
            System.out.print("Ordem da Árvore B: ");
            String input = sc.nextLine();
            Integer ordem = tryParse(input);
            while (ordem == null || ordem < 3) {
                System.out.print("A Ordem da Árvore B precisa ser um inteiro maior ou igual a 3: ");
                input = sc.nextLine();
                ordem = tryParse(input);
            }

            // ---------- abrir arquivos ----------
            br = new BufferedReader(new FileReader("TP02/Dataset/Books.csv"));

            rafLivros = new RandomAccessFile("TP02/livros.bin", "rw");
            rafLivros.setLength(0);        // limpa se já existia
            rafLivros.writeInt(0);         // placeholder do número de livros (posição 0..3)

            arqArvore = new File("TP02/arvore.bin");
            arvore = new ArvoreBMais(arqArvore, ordem);

            // ---------- laço de geração ----------
            br.readLine(); // cabeçalho do CSV

            int i = 1;
            String linha = br.readLine();
            while (linha != null) {
                String[] campos = parseCSV(linha);

                String autoresLinha = campos[1];
                if (autoresLinha.charAt(0) == '"') {
                    autoresLinha = autoresLinha.substring(1, autoresLinha.length() - 1);
                }
                String[] autores = autoresLinha.split(",");

                String generosLinha = campos[3];
                if (generosLinha.charAt(0) == '"') {
                    generosLinha = generosLinha.substring(1, generosLinha.length() - 1);
                }
                String[] generos = generosLinha.split(",");

                Integer pages = campos[2].equals("Unknown") ? -1 : Integer.parseInt(campos[2]);
                Float qtdReviews = campos[8].equals("No rating") ? -1 : Float.parseFloat(campos[8]);

                Livro livro = new Livro(
                        i++,
                        campos[0],
                        autores,
                        pages,
                        generos,
                        campos[4],
                        campos[5],
                        campos[6],
                        campos[7],
                        qtdReviews,
                        Integer.parseInt(campos[9]),
                        campos[10]
                );

                // >>> posição do registro no arquivo de dados
                long posicao = rafLivros.getFilePointer();

                // grava o registro
                rafLivros.writeInt(livro.getId());
                rafLivros.writeInt(livro.getTamanhoEmBytes());
                rafLivros.writeBoolean(false); // lápide: não removido
                rafLivros.writeUTF(livro.getTitulo());

                rafLivros.writeInt(livro.getAutores().length);
                for (String autor : livro.getAutores()) {
                    rafLivros.writeUTF(autor);
                }

                rafLivros.writeInt(livro.getPaginas());

                rafLivros.writeInt(livro.getGeneros().length);
                for (String genero : livro.getGeneros()) {
                    rafLivros.writeUTF(genero);
                }

                rafLivros.writeUTF(livro.getDescricao());
                rafLivros.writeLong(livro.getDataPublicacao());
                rafLivros.writeUTF(livro.getEditora());

                rafLivros.writeChar(livro.getLingua().charAt(0));
                rafLivros.writeChar(livro.getLingua().charAt(1));

                rafLivros.writeFloat(livro.getMediaReviews());
                rafLivros.writeInt(livro.getQtdReviews());
                rafLivros.writeUTF(livro.getThumbnail());

                // >>> insere no índice B-Tree
                arvore.insere(livro.getId(), posicao);

                System.out.println(livro);

                linha = br.readLine();
            }

            // ---------- finalização ----------
            rafLivros.seek(0);
            rafLivros.writeInt(i - 1); // número total de livros

            rafLivros.close();
            arvore.close();
            br.close();
            sc.close();

        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método para tratar uma linha do CSV
    // (tratando o caso dos autores que temos que ignorar as virgulas para o split)
    private static String[] parseCSV(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean dentroAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);

            if (c == '"') {
                if (dentroAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++; // pula a próxima aspas
                } else {
                    dentroAspas = !dentroAspas;
                }
            } else if (c == ',' && !dentroAspas) {
                campos.add(campo.toString().trim());
                campo.setLength(0);
            } else {
                campo.append(c);
            }
        }

        campos.add(campo.toString().trim());
        return campos.toArray(new String[0]);
    }

    // Source - https://stackoverflow.com/a/1486082
    // Posted by Jon Skeet, modified by community.
    public static Integer tryParse(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}