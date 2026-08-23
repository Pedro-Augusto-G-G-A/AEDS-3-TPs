package TP01;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
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

            arqBin = new FileOutputStream("livros.bin");
            dos = new DataOutputStream(arqBin);

            br.readLine();

            int i = 1;

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

                Float qtdReviews = campos[9].equals("No rating") ? -1 : Float.parseFloat(campos[9]);

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

                System.out.println(livro);

                linha = br.readLine();
            }

            // dos.writeInt(j1.idLivro);
            // dos.writeUTF(j1.nome);
            // dos.writeFloat(j1.pontos);

            // dos.writeInt(j2.idLivro);
            // dos.writeUTF(j2.nome);
            // dos.writeFloat(j2.pontos);

            // dos.writeInt(j3.idLivro);
            // dos.writeUTF(j3.nome);
            // dos.writeFloat(j3.pontos);

            dos.close();
            arqBin.close();

            // Livro j_temp= new Livro();

            // arq2 =  new FileInputStream("../dados/livro_ds.db");
            // dis = new DataInputStream(arq2); //conecta o fluxo de entrada de dados ao arquivo

            // j_temp.idLivro= dis.readInt();
            // j_temp.nome=dis.readUTF();  
            // j_temp.pontos=dis.readFloat();
            // System.out.println(j_temp); 
            
            // j_temp.idLivro= dis.readInt();
            // j_temp.nome=dis.readUTF();  
            // j_temp.pontos=dis.readFloat();
            // System.out.println(j_temp); 
           
            // j_temp.idLivro= dis.readInt();
            // j_temp.nome=dis.readUTF();  
            // j_temp.pontos=dis.readFloat();
            // System.out.println(j_temp); 
           
            

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

}
