package TP03;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/*
 * Busca de padrões a base de livros, com KMP e Boyer-Moore.
 *
 * Percorre o arquivo registro a registro, ignora os que têm lápide e procura
 * o padrão nos campos de texto escolhidos (todos ou só um): título, autores,
 * gêneros, descrição, editora, língua e thumbnail. Cada campo é pesquisado
 * separado, então o padrão nunca casa atravessando dois campos.
 */

public class CasamentoPadroes {

    public static final int KMP_ = 1;
    public static final int BOYER_MOORE = 2;
    public static final int AMBOS = 3;

    public static final String[] NOMES_CAMPOS =
            { "titulo", "autores", "generos", "descricao", "editora", "lingua", "thumbnail" };

    // uma ocorrência encontrada
    public static class Ocorrencia {
        public final int id;
        public final String campo;
        public final int posicao;   // posição dentro do texto do campo (0-based)
        public final String trecho; // pedaço do campo ao redor da ocorrência

        Ocorrencia(int id, String campo, int posicao, String trecho) {
            this.id = id;
            this.campo = campo;
            this.posicao = posicao;
            this.trecho = trecho;
        }
    }

    // resultado completo de uma busca
    public static class Resultado {
        public final List<Ocorrencia> ocorrencias = new ArrayList<>(); // do 1º algoritmo executado
        public int livrosComOcorrencia = 0;
        public int livrosVarridos = 0;
        public long tempoKmp = 0;          // nanossegundos (só o tempo do casamento, sem leitura de disco)
        public long tempoBoyerMoore = 0;
        public boolean executouKmp, executouBoyerMoore;
        public boolean algoritmosConcordam = true; // KMP e BM acharam exatamente as mesmas posições?
    }

    //---------------------------------------- núcleo

    // retorna o texto de cada campo (mesma ordem de NOMES_CAMPOS)
    private static String[] textosDoLivro(Livro l) {
        return new String[] {
                l.getTitulo(),
                String.join(", ", l.getAutores()),
                String.join(", ", l.getGeneros()),
                l.getDescricao(),
                l.getEditora(),
                l.getLingua(),
                l.getThumbnail()
        };
    }

    // minúsculas char a char (não usa toLowerCase() da String porque ele pode mudar o tamanho do texto)
    static String minusculas(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) c[i] = Character.toLowerCase(c[i]);
        return new String(c);
    }

    private static String trecho(String texto, int pos, int tamPadrao) {
        int ini = Math.max(0, pos - 20);
        int fim = Math.min(texto.length(), pos + tamPadrao + 20);
        return (ini > 0 ? "..." : "") + texto.substring(ini, fim) + (fim < texto.length() ? "..." : "");
    }

    /*
     * Varre o arquivo e procura o padrão.
     *  algoritmo: KMP_, BOYER_MOORE ou AMBOS (roda os dois, compara tempo e resultados)
     *  campo:     índice em NOMES_CAMPOS, ou -1 para todos
     */

    public static Resultado buscar(RandomAccessFile raf, int algoritmo, String padrao,
                                int campo, boolean ignorarMaiusculas) throws IOException {
        Resultado res = new Resultado();
        String p = ignorarMaiusculas ? minusculas(padrao) : padrao;

        res.executouKmp = (algoritmo == KMP_ || algoritmo == AMBOS);
        res.executouBoyerMoore = (algoritmo == BOYER_MOORE || algoritmo == AMBOS);
        KMP kmp = res.executouKmp ? new KMP(p) : null;
        BoyerMoore bm = res.executouBoyerMoore ? new BoyerMoore(p) : null;

        long tamanhoArquivo = raf.length();
        long posicao = 4; // pula o cabeçalho

        while (posicao < tamanhoArquivo) {
            raf.seek(posicao);
            int id = raf.readInt();
            int tamanho = raf.readInt();
            Livro livro = CRUD.lerRegistro(raf, id); // null => excluído (lápide)
            posicao += 4 + 4 + tamanho;
            if (livro == null) continue;

            res.livrosVarridos++;
            String[] textos = textosDoLivro(livro);
            boolean achouNoLivro = false;

            for (int c = 0; c < textos.length; c++) {
                if (campo != -1 && campo != c) continue;
                String original = textos[c];
                String texto = ignorarMaiusculas ? minusculas(original) : original;

                List<Integer> posKmp = null, posBm = null;
                if (kmp != null) {
                    long ini = System.nanoTime();
                    posKmp = kmp.buscar(texto);
                    res.tempoKmp += System.nanoTime() - ini;
                }
                if (bm != null) {
                    long ini = System.nanoTime();
                    posBm = bm.buscar(texto);
                    res.tempoBoyerMoore += System.nanoTime() - ini;
                }
                if (posKmp != null && posBm != null && !posKmp.equals(posBm)) {
                    res.algoritmosConcordam = false;
                }

                List<Integer> posicoes = (posKmp != null) ? posKmp : posBm;
                for (int pos : posicoes) {
                    res.ocorrencias.add(new Ocorrencia(id, NOMES_CAMPOS[c], pos, trecho(original, pos, p.length())));
                    achouNoLivro = true;
                }
            }
            if (achouNoLivro) res.livrosComOcorrencia++;
        }
        return res;
    }

    // ----------------------------------------- menu

    private static final int MAX_LINHAS = 30; // quantas ocorrências mostrar na tela

    private static String ms(long nanos) {
        return String.format(new Locale("pt", "BR"), "%.3f ms", nanos / 1_000_000.0);
    }

    public static void menu(RandomAccessFile raf, Scanner scanner) throws IOException {
        System.out.println("\n--- Casamento de padrões ---");
        System.out.print("Padrão a procurar: ");
        String padrao = scanner.nextLine();
        if (padrao.isEmpty()) {
            System.out.println("O padrão não pode ser vazio.");
            return;
        }

        System.out.println("Onde procurar?");
        System.out.println("0 - Todos os campos de texto");
        for (int i = 0; i < NOMES_CAMPOS.length; i++) {
            System.out.println((i + 1) + " - " + NOMES_CAMPOS[i]);
        }
        int escolhaCampo = lerOpcao(scanner, "Escolha: ", 0, NOMES_CAMPOS.length);
        int campo = escolhaCampo - 1; // 0 vira -1 (todos)

        System.out.println("Algoritmo:");
        System.out.println("1 - KMP");
        System.out.println("2 - Boyer-Moore");
        System.out.println("3 - Os dois (compara tempo e confere os resultados)");
        int algoritmo = lerOpcao(scanner, "Escolha: ", 1, 3);

        System.out.print("Ignorar maiúsculas/minúsculas? (s/n): ");
        boolean ignorar = scanner.nextLine().trim().equalsIgnoreCase("s");

        Resultado r = buscar(raf, algoritmo, padrao, campo, ignorar);

        System.out.println("\nPadrão \"" + padrao + "\" em "
                + (campo == -1 ? "todos os campos de texto" : "campo " + NOMES_CAMPOS[campo])
                + " (" + r.livrosVarridos + " livros varridos)");

        if (r.ocorrencias.isEmpty()) {
            System.out.println("Nenhuma ocorrência encontrada.");
        } else {
            System.out.println(r.ocorrencias.size() + " ocorrência(s) em " + r.livrosComOcorrencia + " livro(s):");
            int mostrar = Math.min(MAX_LINHAS, r.ocorrencias.size());
            for (int i = 0; i < mostrar; i++) {
                Ocorrencia o = r.ocorrencias.get(i);
                System.out.println("ID " + o.id + " | " + o.campo + " | posição " + o.posicao + " | " + o.trecho);
            }
            if (r.ocorrencias.size() > mostrar) {
                System.out.println("... (mais " + (r.ocorrencias.size() - mostrar) + " ocorrência(s) não exibida(s))");
            }
        }

        if (r.executouKmp) System.out.println("Tempo KMP: " + ms(r.tempoKmp));
        if (r.executouBoyerMoore) System.out.println("Tempo Boyer-Moore: " + ms(r.tempoBoyerMoore));
        if (r.executouKmp && r.executouBoyerMoore) {
            System.out.println(r.algoritmosConcordam
                    ? "Os dois algoritmos encontraram exatamente as mesmas ocorrências."
                    : "ATENÇÃO: os algoritmos divergiram nos resultados!");
            if (r.tempoKmp < r.tempoBoyerMoore) System.out.println("Mais rápido nesta busca: KMP");
            else if (r.tempoBoyerMoore < r.tempoKmp) System.out.println("Mais rápido nesta busca: Boyer-Moore");
            else System.out.println("Empate no tempo.");
        }
    }

    private static int lerOpcao(Scanner scanner, String msg, int min, int max) {
        while (true) {
            System.out.print(msg);
            try {
                int v = Integer.parseInt(scanner.nextLine().trim());
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException e) {
                // cai na mensagem abaixo
            }
            System.out.println("Digite um número entre " + min + " e " + max);
        }
    }
}
