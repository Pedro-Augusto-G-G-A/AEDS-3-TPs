package TP03;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * Parte de sistema da compressão: arquivos, versões, tempos e relatórios.
 * Os algoritmos estão em Huffman.java e LZW.java.
 *
 * Os arquivos gerados seguem o padrão nomeArquivo + nomeAlgoritmo + "Compressao" + X
 * (ex.: livrosHuffmanCompressao1, livrosLZWCompressao1). Os dois algoritmos usam
 * sempre o mesmo X em cada execução e ficam na subpasta criada
 * na primeira compressão.
 */

public class Compressao {

    public static final String ALG_HUFFMAN = "Huffman";
    public static final String ALG_LZW = "LZW";

    // nome da subpasta onde ficam os arquivos comprimidos
    public static final String PASTA_COMPACTADOS = "compactados";

    // ------------------------------------- utilidades

    /*
     * Checksum simples (FNV-1a de 32 bits). Fica gravado no cabeçalho do arquivo 
    comprimido para a descompressão detectar arquivo corrompido.
     */

    public static int checksum(byte[] dados) {
        int hash = 0x811C9DC5;
        for (byte b : dados) {
            hash ^= (b & 0xFF);
            hash *= 0x01000193;
        }
        return hash;
    }

    // "livros.bin" -> "livros" (nomeArquivo usado no nome dos comprimidos)
    private static String nomeBase(Path fonte) {
        String nome = fonte.getFileName().toString();
        int ponto = nome.lastIndexOf('.');
        return ponto > 0 ? nome.substring(0, ponto) : nome;
    }

    // pasta onde ficam os comprimidos
    private static Path pastaCompactados(Path fonte) {
        return fonte.toAbsolutePath().getParent().resolve(PASTA_COMPACTADOS);
    }

    // monta o caminho do arquivo comprimido: <pasta>/compactados/<nomeArquivo><Algoritmo>Compressao<X>
    public static Path caminhoComprimido(Path fonte, String algoritmo, int versao) {
        return pastaCompactados(fonte).resolve(nomeBase(fonte) + algoritmo + "Compressao" + versao);
    }

    /*
     * Descobre a próxima versão livre: olha os arquivos existentes na pasta, pega o maior X 
        entre as versões de Huffman e de LZW e soma 1 (1 se ainda não existe nenhuma).
     */

    public static int proximaVersao(Path fonte) {
        List<Integer> existentes = versoesExistentes(fonte);
        int maior = 0;
        for (int v : existentes) {
            if (v > maior) maior = v;
        }
        return maior + 1;
    }

    // lista (sem repetir, em ordem crescente) as versões que têm pelo menos 1 arquivo comprimido
    public static List<Integer> versoesExistentes(Path fonte) {
        List<Integer> versoes = new ArrayList<>();
        File pasta = pastaCompactados(fonte).toFile(); // se a pasta ainda não existe, listFiles() devolve null
        File[] arquivos = pasta.listFiles();
        if (arquivos == null) return versoes;

        String base = nomeBase(fonte);
        for (File f : arquivos) {
            for (String alg : new String[] { ALG_HUFFMAN, ALG_LZW }) {
                String prefixo = base + alg + "Compressao";
                String nome = f.getName();
                if (nome.startsWith(prefixo)) {
                    String sufixo = nome.substring(prefixo.length());
                    if (!sufixo.isEmpty() && sufixo.chars().allMatch(Character::isDigit) && sufixo.length() < 9) {
                        int v = Integer.parseInt(sufixo);
                        if (!versoes.contains(v)) versoes.add(v);
                    }
                }
            }
        }
        java.util.Collections.sort(versoes);
        return versoes;
    }

    // ganho (+) ou perda (-) em %: (original - comprimido) / original * 100
    public static double percentualGanho(long original, long comprimido) {
        if (original == 0) return 0;
        return (original - comprimido) * 100.0 / original;
    }

    private static String formatarGanho(double ganho) {
        if (ganho >= 0) {
            return String.format(new Locale("pt", "BR"), "GANHO de %.2f%%", ganho);
        }
        return String.format(new Locale("pt", "BR"), "PERDA de %.2f%%", -ganho);
    }

    private static String ms(long nanos) {
        return String.format(new Locale("pt", "BR"), "%.3f ms", nanos / 1_000_000.0);
    }

    // --------------------------------------- compressão

    /*
    Comprime a base inteira com Huffman e LZW, gera livrosHuffmanCompressaoX e
    livrosLZWCompressaoX e mostra ganho/perda, tempo e o melhor algoritmo.
    Retorna a versão criada. O tempo medido é só o do algoritmo, sem leitura/gravação em disco.
    */

    public static int comprimirBase(Path fonte) throws IOException {
        byte[] original = Files.readAllBytes(fonte);
        int versao = proximaVersao(fonte);
        Files.createDirectories(pastaCompactados(fonte)); // cria a subpasta se ainda não existir

        System.out.println("\n--- Compressão da base de dados ---");
        System.out.println("Arquivo: " + fonte.getFileName() + " (" + original.length + " bytes)");
        System.out.println("Versão da compressão: " + versao);

        // Huffman
        long ini = System.nanoTime();
        byte[] huff = Huffman.comprimir(original);
        long tempoHuff = System.nanoTime() - ini;
        Path arqHuff = caminhoComprimido(fonte, ALG_HUFFMAN, versao);
        Files.write(arqHuff, huff);

        // LZW
        ini = System.nanoTime();
        byte[] lzw = LZW.comprimir(original);
        long tempoLzw = System.nanoTime() - ini;
        Path arqLzw = caminhoComprimido(fonte, ALG_LZW, versao);
        Files.write(arqLzw, lzw);

        double ganhoHuff = percentualGanho(original.length, huff.length);
        double ganhoLzw = percentualGanho(original.length, lzw.length);

        System.out.println("\nHuffman -> " + arqHuff.getFileName() + ": " + huff.length + " bytes | "
                + formatarGanho(ganhoHuff) + " | tempo: " + ms(tempoHuff));
        System.out.println("LZW     -> " + arqLzw.getFileName() + ": " + lzw.length + " bytes | "
                + formatarGanho(ganhoLzw) + " | tempo: " + ms(tempoLzw));

        System.out.println("\nComparação (compressão):");
        if (huff.length < lzw.length) {
            System.out.println("- Menor arquivo: Huffman (" + (lzw.length - huff.length) + " bytes a menos que o LZW)");
        } else if (lzw.length < huff.length) {
            System.out.println("- Menor arquivo: LZW (" + (huff.length - lzw.length) + " bytes a menos que o Huffman)");
        } else {
            System.out.println("- Os dois geraram arquivos do mesmo tamanho");
        }
        imprimirMaisRapido(tempoHuff, tempoLzw);

        return versao;
    }

    private static void imprimirMaisRapido(long tempoHuff, long tempoLzw) {
        if (tempoHuff < tempoLzw) {
            System.out.println("- Mais rápido: Huffman (" + ms(tempoLzw - tempoHuff) + " a menos que o LZW)");
        } else if (tempoLzw < tempoHuff) {
            System.out.println("- Mais rápido: LZW (" + ms(tempoHuff - tempoLzw) + " a menos que o Huffman)");
        } else {
            System.out.println("- Tempos iguais");
        }
    }

    // -------------------------------------- descompressão
    
    /*
    Descomprime a versão X com Huffman e LZW (os que existirem), compara tempo e
    resultado e substitui o arquivo de dados pelo recuperado. Retorna true se houve
    a substituição.

    Atenção: feche o arquivo de dados antes de chamar (o Menu já faz isso) e refaça
    os índices depois (Reindexacao), porque o arquivo restaurado pode ser diferente
    do atual, por exemplo se houve inserções ou alterações depois da compressão.
    */
    public static boolean descomprimirBase(Path fonte, int versao) throws IOException {
        Path arqHuff = caminhoComprimido(fonte, ALG_HUFFMAN, versao);
        Path arqLzw = caminhoComprimido(fonte, ALG_LZW, versao);
        boolean temHuff = Files.exists(arqHuff);
        boolean temLzw = Files.exists(arqLzw);

        if (!temHuff && !temLzw) {
            System.out.println("Não existe nenhum arquivo da versão " + versao + ".");
            return false;
        }

        System.out.println("\n--- Descompressão da versão " + versao + " ---");

        byte[] recuperadoHuff = null;
        byte[] recuperadoLzw = null;
        long tempoHuff = 0, tempoLzw = 0;

        if (temHuff) {
            byte[] comp = Files.readAllBytes(arqHuff);
            long ini = System.nanoTime();
            try {
                recuperadoHuff = Huffman.descomprimir(comp);
                tempoHuff = System.nanoTime() - ini;
                System.out.println("Huffman <- " + arqHuff.getFileName() + ": " + recuperadoHuff.length
                        + " bytes recuperados | tempo: " + ms(tempoHuff));
            } catch (IOException e) {
                System.out.println("Huffman: falha ao descomprimir (" + e.getMessage() + ")");
            }
        } else {
            System.out.println("Huffman: arquivo " + arqHuff.getFileName() + " não existe.");
        }

        if (temLzw) {
            byte[] comp = Files.readAllBytes(arqLzw);
            long ini = System.nanoTime();
            try {
                recuperadoLzw = LZW.descomprimir(comp);
                tempoLzw = System.nanoTime() - ini;
                System.out.println("LZW     <- " + arqLzw.getFileName() + ": " + recuperadoLzw.length
                        + " bytes recuperados | tempo: " + ms(tempoLzw));
            } catch (IOException e) {
                System.out.println("LZW: falha ao descomprimir (" + e.getMessage() + ")");
            }
        } else {
            System.out.println("LZW: arquivo " + arqLzw.getFileName() + " não existe.");
        }

        if (recuperadoHuff == null && recuperadoLzw == null) {
            System.out.println("Nenhum algoritmo conseguiu recuperar o arquivo. O arquivo de dados NÃO foi alterado.");
            return false;
        }

        // comparação entre os dois algoritmos (só se os dois funcionaram)
        byte[] recuperado;
        if (recuperadoHuff != null && recuperadoLzw != null) {
            System.out.println("\nComparação (descompressão):");
            imprimirMaisRapido(tempoHuff, tempoLzw);
            if (!java.util.Arrays.equals(recuperadoHuff, recuperadoLzw)) {
                System.out.println("ERRO: os dois algoritmos recuperaram conteúdos DIFERENTES. O arquivo de dados NÃO foi alterado.");
                return false;
            }
            System.out.println("- Os dois recuperaram exatamente o mesmo conteúdo.");
            recuperado = recuperadoHuff;
        } else {
            recuperado = (recuperadoHuff != null) ? recuperadoHuff : recuperadoLzw;
        }

        // substitui o arquivo de dados: grava num temporário e move por cima, pra nunca
        // deixar o arquivo de dados pela metade se algo falhar no meio da escrita
        Path temporario = fonte.toAbsolutePath().resolveSibling(fonte.getFileName() + ".tmp");
        Files.write(temporario, recuperado);
        Files.move(temporario, fonte, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Arquivo de dados " + fonte.getFileName() + " substituído pelo arquivo recuperado.");
        return true;
    }
}
