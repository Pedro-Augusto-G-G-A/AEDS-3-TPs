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

    // numCaminhos = quantidade de arquivos temporários usados (antes era fixo em 4)
    // maxRegistros = tamanho do heap usado na ordenação (antes era fixo em 15)
    public static void main(Path fonte, int numCaminhos, int maxRegistros) {
        if (numCaminhos < 2) {
            System.out.println("O número de caminhos precisa ser pelo menos 2");
            return;
        }
        if (maxRegistros < 1) {
            System.out.println("O número máximo de registros em memória precisa ser pelo menos 1");
            return;
        }

        try {
            // cria os arquivos temporários (agora em quantidade variável, um vetor pra cada conjunto)
            Path[] tempsA = new Path[numCaminhos];
            Path[] tempsB = new Path[numCaminhos];
            for (int i = 0; i < numCaminhos; i++) {
                tempsA[i] = Paths.get("TP01/tempA" + i + ".bin");
                tempsB[i] = Paths.get("TP01/tempB" + i + ".bin");
            }

            RandomAccessFile rafFonte = new RandomAccessFile(fonte.toString(), "r");

            RandomAccessFile[] rafsA = new RandomAccessFile[numCaminhos];
            RandomAccessFile[] rafsB = new RandomAccessFile[numCaminhos];

            // Lê o ultimoId e já salva no início de cada arquivo temporario
            int ultimoId = rafFonte.readInt();

            for (int i = 0; i < numCaminhos; i++) {
                rafsA[i] = new RandomAccessFile(tempsA[i].toString(), "rw");
                rafsA[i].setLength(0);
                rafsA[i].writeInt(ultimoId);

                rafsB[i] = new RandomAccessFile(tempsB[i].toString(), "rw");
                rafsB[i].setLength(0);
                rafsB[i].writeInt(ultimoId);
            }

            // gera as runs ordenadas com a heap e distribui entre os arquivos de "A"
            // (isso já remove os registros deletados e os buracos de atualizações,
            // já que só escrevemos os registros válidos)
            distribuirComHeap(rafFonte, rafsA, maxRegistros);
            rafFonte.close();

            for (int i = 0; i < numCaminhos; i++) {
                rafsA[i].close();
                rafsB[i].close();
            }

            // intercala os arquivos até sobrar só 1 não vazio (mesma ideia de antes,
            // só que agora com um vetor de arquivos em vez de 4 fixos)
            Path[] atual = tempsA;
            Path[] proximo = tempsB;

            while (true) {
                // Verifica quais arquivos estão vazios e conta quantos não estão
                int naoVazios = 0;
                Path unico = null;
                for (Path p : atual) {
                    if (Files.size(p) > 4) { // 4 bytes = só o cabeçalho
                        naoVazios++;
                        unico = p;
                    }
                }

                // Se só tem 1 arquivo não vazio, terminamos
                if (naoVazios <= 1) {
                    if (unico == null) {
                        unico = atual[0];
                    }
                    // Renomeia sobreescrevendo o arquivo final pra livros.bin
                    Files.move(unico, fonte, StandardCopyOption.REPLACE_EXISTING);
                    break;
                }

                RandomAccessFile[] entradas = new RandomAccessFile[numCaminhos];
                RandomAccessFile[] saidas = new RandomAccessFile[numCaminhos];
                for (int i = 0; i < numCaminhos; i++) {
                    entradas[i] = new RandomAccessFile(atual[i].toString(), "r");
                    entradas[i].seek(4);

                    saidas[i] = new RandomAccessFile(proximo[i].toString(), "rw");
                    saidas[i].setLength(0);
                    saidas[i].writeInt(ultimoId);
                }

                intercalarKVias(entradas, saidas);

                for (int i = 0; i < numCaminhos; i++) {
                    entradas[i].close();
                    saidas[i].close();
                }

                // troca os papéis: quem era saída vira entrada da próxima passada
                Path[] troca = atual;
                atual = proximo;
                proximo = troca;
            }

            // apaga os temporários
            for (Path p : tempsA) {
                Files.deleteIfExists(p);
            }
            for (Path p : tempsB) {
                Files.deleteIfExists(p);
            }

            System.out.println("Arquivo ordenado!");
        } catch (IOException e) {
            System.err.println("Ordenação falhou: " + e.getMessage());
        }
    }

    private static void distribuirComHeap(RandomAccessFile rafFonte, RandomAccessFile[] destinos, int maxRegistros) throws IOException {
        //Cria uma Heap para ordenação externa
        PriorityQueue<Map.Entry<Integer, Livro>> heap =
                new PriorityQueue<>(maxRegistros,
                        Comparator.comparingInt((Map.Entry<Integer, Livro> e) -> e.getKey())
                                  .thenComparingInt(e -> e.getValue().getId())); // menor ID primeiro

        int chave = 0;
        Integer ultimoLivroDest = null; // Último livro escrito no destino

        preencherHeap(rafFonte, heap, chave, maxRegistros);

        while (!heap.isEmpty()) {
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

            preencherHeap(rafFonte, heap, chave, maxRegistros);

            int destinoIndex = registro.getKey() % destinos.length;
            write(destinos[destinoIndex], livroAtual);
        }
    }

    private static Integer getProximoIdDaFonte(RandomAccessFile rafFonte) throws IOException {
        while (rafFonte.getFilePointer() < rafFonte.length()) {
            long posInicio = rafFonte.getFilePointer();
            int id = rafFonte.readInt();
            int tamRegistro = rafFonte.readInt();
            boolean deletado = rafFonte.readBoolean();

            if (!deletado) {
                // Encontrou um registro válido
                rafFonte.seek(posInicio); // Volta para a posição original
                return id;
            }

            // pula registro deletado (lápide)
            rafFonte.seek(posInicio + 4 + 4 + tamRegistro);
        }
        return null;
    }

    private static void preencherHeap(RandomAccessFile rafFonte,
                                    PriorityQueue<Map.Entry<Integer, Livro>> heap,
                                    int chave, int maxRegistros) throws IOException {
        while (heap.size() < maxRegistros && rafFonte.getFilePointer() < rafFonte.length()) {
            Livro livro = lerProximoLivro(rafFonte);
            if (livro != null) {
                heap.offer(new AbstractMap.SimpleEntry<>(chave, livro));
            }
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

    // faz a mesma coisa que o intercalar antigo (mescla os arquivos em runs ordenadas),
    // só que agora pra uma quantidade variável de entradas/saídas em vez de só 2
    private static void intercalarKVias(RandomAccessFile[] entradas, RandomAccessFile[] saidas) throws IOException {
        int k = entradas.length;
        int runDeSaida = 0;

        boolean algumaEntradaTemDados = true;

        while (algumaEntradaTemDados) {
            algumaEntradaTemDados = false;

            // último id aceito de cada entrada dentro da run atual, usado pra
            // detectar se o próximo registro ainda pertence à mesma run
            Integer[] ultimoIdAceito = new Integer[k];
            Livro[] atual = new Livro[k];

            for (int i = 0; i < k; i++) {
                atual[i] = lerProximoDaRun(entradas[i], null);
                if (atual[i] != null) {
                    ultimoIdAceito[i] = atual[i].getId();
                }
            }

            RandomAccessFile saidaAtual = saidas[runDeSaida % saidas.length];
            boolean escreveuAlgo = false;

            while (true) {
                int menorIndex = -1;
                for (int i = 0; i < k; i++) {
                    if (atual[i] != null) {
                        if (menorIndex == -1 || atual[i].getId() < atual[menorIndex].getId()) {
                            menorIndex = i;
                        }
                    }
                }

                if (menorIndex == -1) {
                    break; // acabou a run dessa passada em todas as entradas
                }

                write(saidaAtual, atual[menorIndex]);
                escreveuAlgo = true;

                Livro proximo = lerProximoDaRun(entradas[menorIndex], ultimoIdAceito[menorIndex]);
                if (proximo != null) {
                    ultimoIdAceito[menorIndex] = proximo.getId();
                }
                atual[menorIndex] = proximo;
            }

            if (escreveuAlgo) {
                runDeSaida++;
            }

            for (int i = 0; i < k; i++) {
                if (entradas[i].getFilePointer() < entradas[i].length()) {
                    algumaEntradaTemDados = true;
                }
            }
        }
    }

    // lê o próximo registro só se ele ainda for da run atual; se o id vier menor
    // que o último aceito, começou uma run nova, então volta o ponteiro e retorna null
    private static Livro lerProximoDaRun(RandomAccessFile raf, Integer ultimoIdAceito) throws IOException {
        if (raf.getFilePointer() >= raf.length()) {
            return null;
        }

        long posInicio = raf.getFilePointer();
        int id = raf.readInt();
        int tamRegistro = raf.readInt();

        if (ultimoIdAceito != null && id < ultimoIdAceito) {
            // pertence a uma run futura, ainda não é a vez dela
            raf.seek(posInicio);
            return null;
        }

        Livro livro = CRUD.lerRegistro(raf, id);
        raf.seek(posInicio + 4 + 4 + tamRegistro);
        return livro;
    }

    static private void write(RandomAccessFile destino, Livro livro) throws IOException {
        destino.writeInt(livro.id);
        destino.writeInt(livro.getTamanhoEmBytes());
        destino.writeBoolean(false);
        CRUD.salvarDados(destino, livro);
    }
}