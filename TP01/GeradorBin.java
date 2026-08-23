package TP01;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.io.DataOutputStream;

public class GeradorBin {
    public static void main(String[] args){
        // Gravando os dados dos livro em um arquivo binário
        BufferedReader br;
        FileOutputStream arqBin; // arquivo de sapida
        DataOutputStream dos; //fluxo de saída de dados 

        try {

            br = new BufferedReader(new FileReader("TP01/Dataset/Books.csv"));

            arqBin = new FileOutputStream("TP01/livros.bin");
            dos = new DataOutputStream(arqBin);

            br.readLine();

            int i = 1;

            dos.writeInt(0);

            String linha = br.readLine();
            while (linha != null) {
                String[] campos = parseCSV(linha);

                String autoresLinha = campos[1];
                if(autoresLinha.charAt(0) == '\"') {
                    autoresLinha = autoresLinha.substring(1, autoresLinha.length() - 1);
                }
                String[] autores = autoresLinha.split(",");

                String generosLinha = campos[3];
                if(generosLinha.charAt(0) == '\"') {
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

                dos.writeInt(livro.getId());
                dos.writeInt(calcularTamanhoRegistro(livro));
                dos.writeBoolean(false);
                dos.writeUTF(livro.getTitulo());
                dos.writeInt(livro.getAutores().length);
                for(String autor : livro.getAutores()) {
                    dos.writeUTF(autor);
                }
                dos.writeInt(livro.getPaginas());
                dos.writeInt(livro.getGeneros().length);
                for(String genero : livro.getGeneros()) {
                    dos.writeUTF(genero);
                }
                dos.writeUTF(livro.getDescricao());
                dos.writeLong(livro.getDataPublicacao());
                dos.writeUTF(livro.getEditora());
                dos.writeChar(livro.getLingua().charAt(0));
                dos.writeChar(livro.getLingua().charAt(1));
                dos.writeFloat(livro.getMediaReviews());
                dos.writeInt(livro.getQtdReviews());
                dos.writeUTF(livro.getThumbnail());

                System.out.println(livro);

                linha = br.readLine();
            }

            dos.flush();
            dos.close();
            arqBin.close();

            try (RandomAccessFile raf = new RandomAccessFile("TP01/livros.bin", "rw")) {
                raf.seek(0); // Vai pro inicio do arquivo
                raf.writeInt(i - 1); // Escreve o número de IDs
            }

            br.close();

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        
    }

    // Método para tratar uma linha do CSV, transformando-a no formato desejado
    // (tratando o caso dos autores que temos que ignorar as virgulas para o split)
    private static String[] parseCSV(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean dentroAspas = false;
        
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            
            if (c == '"') {
                // Verifica se é aspas duplas escapadas (formato CSV)
                if (dentroAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++; // Pula a próxima aspas
                } else {
                    dentroAspas = !dentroAspas;
                }
            } else if (c == ',' && !dentroAspas) {
                // Fim do campo
                campos.add(campo.toString().trim());
                campo.setLength(0);
            } else {
                campo.append(c);
            }
        }
        
        // Adiciona o último campo
        campos.add(campo.toString().trim());
        
        return campos.toArray(new String[0]);
    }

    static private int calcularTamanhoRegistro(Livro livro) throws IOException {
        int tam = 0;
        
        // Lápide (BOOL)
        tam += 1;
        
        // Título (UTF)
        tam += 4 + getUTFLength(livro.getTitulo());
        
        // Autores array
        tam += 4; // INT: tamanho do array
        for (String autor : livro.getAutores()) {
            tam += 4 + getUTFLength(autor); // INT: tamanho + UTF
        }
        
        // Páginas (INT)
        tam += 4;
        
        // Gêneros array
        tam += 4; // INT: tamanho do array
        for (String genero : livro.getGeneros()) {
            tam += 4 + getUTFLength(genero);
        }
        
        // Descrição (UTF)
        tam += 4 + getUTFLength(livro.getDescricao());
        
        // DataPublicacao (LONG)
        tam += 8;
        
        // Editora (UTF)
        tam += 4 + getUTFLength(livro.getEditora());
        
        // Língua (2 CHARs)
        tam += 2 + 2; // 2 chars = 4 bytes
        
        // MediaReviews (FLOAT)
        tam += 4;
        
        // QtdReviews (INT)
        tam += 4;
        
        // Thumbnail (UTF)
        tam += 4 + getUTFLength(livro.getThumbnail());
        
        return tam;
    }

    // Método para calcular o tamanho de uma string em UTF-8 (DataOutputStream.writeUTF)
    static private int getUTFLength(String str) {
        if (str == null) str = "";
        int utfLength = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 0x0001 && c <= 0x007F) {
                utfLength += 1;
            } else if (c > 0x07FF) {
                utfLength += 3;
            } else {
                utfLength += 2;
            }
        }
        return utfLength;
    }

}
