package TP01;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;

public class Ordenacao {
    public static void main(Path fonte) {
        try {
            Path arqTemp1 = Paths.get("TP01/temp1.bin");
            Path arqTemp2 = Paths.get("TP01/temp2.bin");
            Path arqTemp3 = Paths.get("TP01/temp3.bin");
            Path arqTemp4 = Paths.get("TP01/temp4.bin");

            RandomAccessFile rafFonte = new RandomAccessFile(fonte.toString(), "r");
            RandomAccessFile rafDest1 = new RandomAccessFile(arqTemp1.toString(), "rw");
            RandomAccessFile rafDest2 = new RandomAccessFile(arqTemp2.toString(), "rw");
            RandomAccessFile rafDest3 = new RandomAccessFile(arqTemp3.toString(), "rw");
            RandomAccessFile rafDest4 = new RandomAccessFile(arqTemp4.toString(), "rw");
            
            // Lê o ultimoId e já salva no início de cada arquivo temporario
            int ultimoId = rafFonte.readInt();
            rafDest1.writeInt(ultimoId);
            rafDest2.writeInt(ultimoId);
            rafDest3.writeInt(ultimoId);
            rafDest4.writeInt(ultimoId);

            ordernarComHeap(rafFonte, rafDest1, rafDest2);

            rafFonte.close();
            rafDest1.close();
            rafDest2.close();
            rafDest3.close();
            rafDest4.close();

            rafDest1 = new RandomAccessFile(arqTemp1.toString(), "rw");
            rafDest2 = new RandomAccessFile(arqTemp2.toString(), "rw");
            rafDest3 = new RandomAccessFile(arqTemp3.toString(), "rw");
            rafDest4 = new RandomAccessFile(arqTemp4.toString(), "rw");

            //enquanto não houver somente um arquivo vazio
            boolean continuar = true;
            int fase = 0;
            while(continuar) {
                 // Verifica quais arquivos estão vazios
                boolean vazio1 = Files.size(arqTemp1) <= 4; // 4 bytes de cabeçalho
                boolean vazio2 = Files.size(arqTemp2) <= 4;
                boolean vazio3 = Files.size(arqTemp3) <= 4;
                boolean vazio4 = Files.size(arqTemp4) <= 4;

                // Conta quantos arquivos não estão vazios
                int naoVazios = 0;
                if (!vazio1) naoVazios++;
                if (!vazio2) naoVazios++;
                if (!vazio3) naoVazios++;
                if (!vazio4) naoVazios++;

                // Se só tem 1 arquivo não vazio, terminamos
                if (naoVazios <= 1) {
                    continuar = false;
                    break;
                }
                
                if (fase == 0) {
                    // Intercala 1 e 2 escrevendo em 3 e 4
                    intercalar(rafDest1, rafDest2, rafDest3, rafDest4);

                    rafDest1.setLength(0);
                    rafDest1.writeInt(ultimoId);
                    rafDest2.setLength(0);
                    rafDest2.writeInt(ultimoId);
                        
                    fase = 1; // Próxima fase intercala 3 e 4
                } else {
                    // Intercala 3 e 4 escrevendo em 1
                    intercalar(rafDest3, rafDest4, rafDest1, rafDest2);

                    rafDest3.setLength(0);
                    rafDest3.writeInt(ultimoId);
                    rafDest4.setLength(0);
                    rafDest4.writeInt(ultimoId);
                    
                    fase = 0; // Próxima fase intercala 1 e 2
                }
            }

            // Renomeia o arquivo final (o único que não está vazio)
            Path arquivoFinal = null;
            if (Files.size(arqTemp1) > 4) arquivoFinal = arqTemp1;
            else if (Files.size(arqTemp2) > 4) arquivoFinal = arqTemp2;
            else if (Files.size(arqTemp3) > 4) arquivoFinal = arqTemp3;
            else if (Files.size(arqTemp4) > 4) arquivoFinal = arqTemp4;

            rafDest1.close();
            rafDest2.close();
            rafDest3.close();
            rafDest4.close();

            // Renomeia sobreescrevendo livrosTemp.bin para livros.bin
            Files.move(arquivoFinal, fonte, StandardCopyOption.REPLACE_EXISTING);

            java.nio.file.Files.deleteIfExists(arqTemp1);
            java.nio.file.Files.deleteIfExists(arqTemp2);
            java.nio.file.Files.deleteIfExists(arqTemp3);
            java.nio.file.Files.deleteIfExists(arqTemp4);

            System.out.println("Arquivo Ordenado com sucesso!");
        } catch (IOException e) {
            System.err.println("Ordenação falhou: " + e.getMessage());
        }
    }

    private static void ordernarComHeap(RandomAccessFile rafFonte, RandomAccessFile rafDest1, RandomAccessFile rafDest2) throws IOException {
        //Cria uma Heap para ordenação externa
        int maxSize = 15;
        PriorityQueue<Map.Entry<Integer, Livro>> heap =
                                    new PriorityQueue<>(maxSize,
                                                        Comparator.comparingInt((Map.Entry<Integer, Livro> e) -> e.getKey())
                                                                                    .thenComparingInt(e -> e.getValue().getId()) // Menor ID primeiro
                                                                                );

        int chave = 0;
        Integer ultimoLivroDest = null; // Último livro escrito no destino

        preencherHeapComProximosRegistros(rafFonte, heap, chave, maxSize);

        while(!heap.isEmpty()) {
            Map.Entry<Integer, Livro> registro = heap.poll();

            Livro livroAtual = registro.getValue();

            Integer proximoId = getProximoIdDaFonte(rafFonte);
            
            // Verifica se precisa incrementar a chave
            if (ultimoLivroDest != null && proximoId != null && proximoId < ultimoLivroDest) {
                chave++;
                ultimoLivroDest = null;
            } else {
                ultimoLivroDest = livroAtual.getId();
            }

            preencherHeapComProximosRegistros(rafFonte, heap, chave, maxSize);

            if(registro.getKey() % 2 == 0) {
                write(rafDest1, livroAtual);
            } else {
                write(rafDest2, livroAtual);
            }
        }
    }

    private static Integer getProximoIdDaFonte(RandomAccessFile rafFonte) throws IOException {
        if (rafFonte.getFilePointer() >= rafFonte.length()) {
            return null;
        }
        
        while (rafFonte.getFilePointer() < rafFonte.length()) {
            long posAtual = rafFonte.getFilePointer();
            long posInicio = rafFonte.getFilePointer();
            int id = rafFonte.readInt();
            int tamRegistro = rafFonte.readInt();
            boolean deletado = rafFonte.readBoolean();
            
            if (!deletado) {
                // Encontrou um registro válido
                rafFonte.seek(posAtual); // Volta para a posição original
                return id;
            }
            
            // Pula registro deletado
            rafFonte.seek(posInicio + 4 + 4 + 1 + tamRegistro);
        }
        return null;
    }

    private static void preencherHeapComProximosRegistros(RandomAccessFile rafFonte,
                                                            PriorityQueue<Map.Entry<Integer, Livro>> heap,
                                                            int chave, int maxSize) throws IOException
    {
        while(heap.size() < maxSize  && rafFonte.getFilePointer() < rafFonte.length()) {
            Livro livro = lerProximoLivro(rafFonte);

            if (livro != null) {
                heap.offer(new AbstractMap.SimpleEntry<>(chave, livro));
            }
        }
    }

    private static void intercalar(RandomAccessFile rafFonte1, RandomAccessFile rafFonte2,
                                    RandomAccessFile rafDest1, RandomAccessFile rafDest2)
                                    throws IOException
    {
        rafFonte1.seek(4);
        rafFonte2.seek(4);
        rafDest1.seek(4);
        rafDest2.seek(4);

        Livro livro1 = lerProximoLivro(rafFonte1);
        Livro livro2 = lerProximoLivro(rafFonte2);

        boolean escreverNoDest1 = true;
        int ultimoIdEscrito = 0; // Último ID escrito

        Livro livroEscolhido;
        while (livro1 != null && livro2 != null) {
            if (livro1.getId() < livro2.getId() && ultimoIdEscrito < livro1.getId()) {
                livroEscolhido = livro1;
                livro1 = lerProximoLivro(rafFonte1);
            } else if (ultimoIdEscrito < livro2.getId()) {
                livroEscolhido = livro2;
                livro2 = lerProximoLivro(rafFonte2);
            } else {
                escreverNoDest1 = !escreverNoDest1; // Alterna o destino
                ultimoIdEscrito = 0;
                continue;
            }

            // Escreve no destino atual
            if (escreverNoDest1) {
                write(rafDest1, livroEscolhido);
            } else {
                write(rafDest2, livroEscolhido);
            }

            ultimoIdEscrito = livroEscolhido.getId();
        }

        // Escreve o restante do arquivo1
        while (livro1 != null) {
            if (livro1.getId() < ultimoIdEscrito) {
                escreverNoDest1 = !escreverNoDest1;
                ultimoIdEscrito = 0;
            }

            if (escreverNoDest1) {
                write(rafDest1, livro1);
            } else {
                write(rafDest2, livro1);
            }
            ultimoIdEscrito = livro1.getId();
            livro1 = lerProximoLivro(rafFonte1);
        }

        // Escreve o restante do arquivo2
        while (livro2 != null) {
            if (livro2.getId() < ultimoIdEscrito) {
                escreverNoDest1 = !escreverNoDest1;
                ultimoIdEscrito = 0;
            }

            if (escreverNoDest1) {
                write(rafDest1, livro2);
            } else {
                write(rafDest2, livro2);
            }
            ultimoIdEscrito = livro2.getId();
            livro2 = lerProximoLivro(rafFonte2);
        }
    }

    static private Livro lerProximoLivro(RandomAccessFile rafFonte) throws IOException {
        if (rafFonte.getFilePointer() >= rafFonte.length()) {
            return null;
        }

        int idAtual = rafFonte.readInt();
        int tamRegistro = rafFonte.readInt();
        long posInicioRegistro = rafFonte.getFilePointer();

        Livro livro = CRUD.lerRegistro(rafFonte, idAtual);

        rafFonte.seek(posInicioRegistro + tamRegistro);
        
        return livro;
    }

    static private void write(RandomAccessFile destino, Livro livro) throws IOException {
        destino.writeInt(livro.id);
        destino.writeInt(livro.getTamanhoEmBytes());
        destino.writeBoolean(false);
        CRUD.salvarDados(destino, livro);
    }
}
