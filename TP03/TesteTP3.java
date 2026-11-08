package TP03;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/*
 * Testes automáticos do TP3. Rodar a partir da pasta que contém TP03/:
 *   java -cp out TP03.TesteTP3
 * Trabalha numa CÓPIA da base (TP03/teste/livros.bin), não mexe no livros.bin original.
 */
public class TesteTP3 {
    static int ok = 0, falhas = 0;

    static void check(boolean cond, String nome) {
        if (cond) { ok++; System.out.println("  [OK]    " + nome); }
        else { falhas++; System.out.println("  [FALHA] " + nome); }
    }

    // referência ingênua só para conferir os resultados (NÃO é usada no sistema)
    static List<Integer> ingenuo(String t, String p) {
        List<Integer> r = new ArrayList<>();
        if (p.isEmpty()) return r;
        for (int i = 0; i + p.length() <= t.length(); i++) if (t.startsWith(p, i)) r.add(i);
        return r;
    }

    static void roundTrip(String nome, byte[] d) throws Exception {
        byte[] h = Huffman.comprimir(d);
        byte[] l = LZW.comprimir(d);
        check(Arrays.equals(d, Huffman.descomprimir(h)), "Huffman round-trip: " + nome + " (" + d.length + " -> " + h.length + ")");
        check(Arrays.equals(d, LZW.descomprimir(l)), "LZW round-trip: " + nome + " (" + d.length + " -> " + l.length + ")");
    }

    public static void main(String[] args) throws Exception {
        Random rnd = new Random(42);

        System.out.println("== 1. Compressão: casos extremos ==");
        roundTrip("vazio", new byte[0]);
        roundTrip("1 byte", new byte[] { 65 });
        byte[] iguais = new byte[100000]; Arrays.fill(iguais, (byte) 7);
        roundTrip("1 símbolo repetido", iguais);
        byte[] zeros = new byte[5000];
        roundTrip("só zeros", zeros);
        byte[] todos = new byte[256 * 4];
        for (int i = 0; i < todos.length; i++) todos[i] = (byte) i;
        roundTrip("todos os 256 valores", todos);
        byte[] aleat = new byte[300000]; rnd.nextBytes(aleat);
        roundTrip("aleatório 300KB (enche o dicionário do LZW)", aleat);
        byte[] baixa = new byte[400000];
        for (int i = 0; i < baixa.length; i++) baixa[i] = (byte) (rnd.nextInt(6) * rnd.nextInt(4));
        roundTrip("baixa entropia 400KB (dicionário cheio)", baixa);
        byte[] kwk = "abababababababababababababababab".getBytes();
        roundTrip("caso especial KwKwK (abab...)", kwk);
        roundTrip("aaaa...", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
        // Huffman com árvore muito desbalanceada (frequências em progressão de Fibonacci)
        java.io.ByteArrayOutputStream fib = new java.io.ByteArrayOutputStream();
        long a = 1, b = 1;
        for (int s = 0; s < 24; s++) { for (long k = 0; k < a; k++) fib.write(s); long c = a + b; a = b; b = c; }
        roundTrip("Fibonacci (árvore profunda)", fib.toByteArray());
        byte[] real = Files.readAllBytes(Paths.get("TP03/livros.bin"));
        roundTrip("livros.bin real", real);

        // corrupção deve ser detectada, não gerar lixo silenciosamente
        byte[] hc = Huffman.comprimir(real); hc[hc.length / 2] ^= 0x55;
        boolean det = false; try { Huffman.descomprimir(hc); } catch (java.io.IOException e) { det = true; }
        check(det, "Huffman detecta arquivo corrompido");
        byte[] lc = LZW.comprimir(real); lc[lc.length / 2] ^= 0x55;
        det = false; try { LZW.descomprimir(lc); } catch (java.io.IOException e) { det = true; }
        check(det, "LZW detecta arquivo corrompido");
        det = false; try { Huffman.descomprimir(LZW.comprimir(real)); } catch (java.io.IOException e) { det = true; }
        check(det, "Huffman recusa arquivo LZW (magic number)");

        System.out.println("\n== 2. KMP e Boyer-Moore x referência ==");
        String[][] fixos = {
            {"", "a"}, {"abc", ""}, {"abc", "abcd"}, {"abc", "abc"}, {"abcabc", "abc"}, {"aaaaa", "aa"},
            {"abcdef", "a"}, {"abcdef", "f"}, {"abcdef", "xyz"}, {"abracadabra", "abra"},
            {"ação e coração", "ção"}, {"anpanman", "anpan"}, {"GCATCGCAGAGAGTATACAGTACG", "GCAGAGAG"},
            {"aabaabaaaabaab", "aabaab"}
        };
        boolean tudoCerto = true;
        for (String[] c : fixos) {
            List<Integer> esp = ingenuo(c[0], c[1]);
            if (!new KMP(c[1]).buscar(c[0]).equals(esp) || !new BoyerMoore(c[1]).buscar(c[0]).equals(esp)) {
                tudoCerto = false; System.out.println("    divergiu: texto=\"" + c[0] + "\" padrao=\"" + c[1] + "\"");
            }
        }
        check(tudoCerto, "casos fixos (vazio, início, fim, sobreposto, ausente, acentos)");
        tudoCerto = true;
        String alfa = "ab";
        for (int it = 0; it < 20000 && tudoCerto; it++) {
            int alf = 1 + rnd.nextInt(3); // alfabetos de 1 a 3 letras (muita repetição = casos difíceis)
            StringBuilder t = new StringBuilder(), p = new StringBuilder();
            int nt = rnd.nextInt(40), np = 1 + rnd.nextInt(6);
            for (int i = 0; i < nt; i++) t.append((char) ('a' + rnd.nextInt(alf)));
            for (int i = 0; i < np; i++) p.append((char) ('a' + rnd.nextInt(alf)));
            List<Integer> esp = ingenuo(t.toString(), p.toString());
            if (!new KMP(p.toString()).buscar(t.toString()).equals(esp) || !new BoyerMoore(p.toString()).buscar(t.toString()).equals(esp)) {
                tudoCerto = false; System.out.println("    divergiu: t=" + t + " p=" + p);
            }
        }
        check(tudoCerto, "20000 casos aleatórios (alfabetos pequenos)");

        System.out.println("\n== 3. Busca na base: arquivo todo e campo específico ==");
        Path teste = Paths.get("TP03/teste/livros.bin");
        if (Files.exists(teste.getParent())) {
            for (File f : teste.getParent().toFile().listFiles()) {
                if (f.isDirectory()) for (File g : f.listFiles()) g.delete();
                f.delete();
            } // limpa restos de execuções anteriores
        }
        Files.createDirectories(teste.getParent());
        Files.copy(Paths.get("TP03/livros.bin"), teste, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (RandomAccessFile raf = new RandomAccessFile(teste.toString(), "r")) {
            String[] padroes = { "the", "Harry", "zzzzqqq", "A", "Penguin", "http", "Fiction" };
            for (String pad : padroes) {
                for (int campo = -1; campo < CasamentoPadroes.NOMES_CAMPOS.length; campo++) {
                    CasamentoPadroes.Resultado r = CasamentoPadroes.buscar(raf, CasamentoPadroes.AMBOS, pad, campo, false);
                    // referência: varre com leitura sequencial e busca ingênua
                    int esperado = contarReferencia(raf, pad, campo);
                    if (!r.algoritmosConcordam || r.ocorrencias.size() != esperado) {
                        tudoCerto = false;
                        System.out.println("    divergiu: pad=" + pad + " campo=" + campo + " obtido=" + r.ocorrencias.size() + " esperado=" + esperado);
                    }
                }
            }
            check(tudoCerto, "KMP e BM batem com a referência (7 padrões x 8 escopos)");
            CasamentoPadroes.Resultado ausente = CasamentoPadroes.buscar(raf, CasamentoPadroes.AMBOS, "zzzzqqq", -1, false);
            check(ausente.ocorrencias.isEmpty(), "padrão inexistente => 0 ocorrências");
            CasamentoPadroes.Resultado maius = CasamentoPadroes.buscar(raf, CasamentoPadroes.AMBOS, "HARRY", 0, true);
            CasamentoPadroes.Resultado maius2 = CasamentoPadroes.buscar(raf, CasamentoPadroes.AMBOS, "HARRY", 0, false);
            check(maius.ocorrencias.size() == contarReferencia(raf, "harry", 0, true)
                  && maius2.ocorrencias.size() == contarReferencia(raf, "HARRY", 0, false)
                  && maius.ocorrencias.size() > maius2.ocorrencias.size(),
                  "ignorar maiúsculas bate com a referência (HARRY: " + maius.ocorrencias.size() + " ignorando x " + maius2.ocorrencias.size() + " exato)");
        }

        System.out.println("\n== 4. Compressão/descompressão de arquivos, versões e reindexação ==");
        // índices da cópia
        File arv = new File("TP03/teste/arvore.bin"), le = new File("TP03/teste/lista_editora.txt"), lg = new File("TP03/teste/lista_genero.txt");
        Reindexacao.reconstruir(teste, arv, 5, le, lg);
        byte[] antes = Files.readAllBytes(teste);

        int v1 = Compressao.comprimirBase(teste);
        int v2 = Compressao.comprimirBase(teste);
        check(v1 == 1 && v2 == 2, "versões incrementam (1 e 2)");
        check(Files.exists(Paths.get("TP03/teste/compactados/livrosHuffmanCompressao1")) && Files.exists(Paths.get("TP03/teste/compactados/livrosLZWCompressao2")),
              "nomes dos arquivos: livrosHuffmanCompressao1 / livrosLZWCompressao2");
        check(Compressao.versoesExistentes(teste).equals(Arrays.asList(1, 2)), "versoesExistentes = [1, 2]");

        // altera a base (CRUD) DEPOIS da compressão: a versão 1 deve restaurar o estado antigo
        ArvoreB arvore = new ArvoreB(arv);
        ListaInvertida lEd = new ListaInvertida(le), lGe = new ListaInvertida(lg);
        int novoId;
        try (RandomAccessFile raf = new RandomAccessFile(teste.toString(), "rw")) {
            Livro novo = new Livro(-1, "Livro de Teste TP3", new String[] { "Autor X" }, 100, new String[] { "Teste" },
                    "descricao teste", "2020-01-01", "Editora Teste", "pt", 4.5f, 10, "http://x");
            novoId = CRUD.create(raf, arvore, lEd, lGe, novo);
            CRUD.delete(raf, arvore, lEd, lGe, 10);
        }
        arvore.close();
        check(!Arrays.equals(antes, Files.readAllBytes(teste)), "base foi alterada pelo CRUD (id novo " + novoId + ", id 10 excluído)");

        boolean subst = Compressao.descomprimirBase(teste, 1);
        check(subst && Arrays.equals(antes, Files.readAllBytes(teste)), "descompressão v1 restaura a base ORIGINAL byte a byte");
        Reindexacao.reconstruir(teste, arv, 5, le, lg);
        arvore = new ArvoreB(arv);
        lEd = new ListaInvertida(le); lGe = new ListaInvertida(lg);
        try (RandomAccessFile raf = new RandomAccessFile(teste.toString(), "rw")) {
            check(CRUD.read(raf, arvore, 10) != null, "após restaurar, id 10 voltou a existir");
            check(CRUD.read(raf, arvore, novoId) == null, "após restaurar, id criado depois da compressão não existe");
            // todos os ids 1..ultimo achados pela árvore reconstruída
            // ids que existem de fato no arquivo (a base original tem 2 ids que já não existem: foram removidos numa ordenação antiga)
            List<Integer> validos = new ArrayList<>();
            long pp = 4;
            while (pp < raf.length()) { raf.seek(pp); int id = raf.readInt(); int tam = raf.readInt(); if (!raf.readBoolean()) validos.add(id); pp += 8 + tam; }
            boolean todosAchados = true;
            for (int id : validos) if (CRUD.read(raf, arvore, id) == null) { todosAchados = false; break; }
            check(todosAchados && validos.size() == 2050, "árvore B reconstruída encontra todos os " + validos.size() + " registros válidos");
        }
        arvore.close();
        check(Compressao.descomprimirBase(teste, 99) == false, "versão inexistente é recusada sem alterar a base");

        // arquivo comprimido corrompido não pode substituir a base
        Path hf = Paths.get("TP03/teste/compactados/livrosHuffmanCompressao2"), lf = Paths.get("TP03/teste/compactados/livrosLZWCompressao2");
        byte[] b1 = Files.readAllBytes(hf), b2 = Files.readAllBytes(lf);
        b1[b1.length / 2] ^= 0x33; b2[b2.length / 2] ^= 0x33;
        Files.write(hf, b1); Files.write(lf, b2);
        byte[] antesCorrupcao = Files.readAllBytes(teste);
        boolean s2 = Compressao.descomprimirBase(teste, 2);
        check(!s2 && Arrays.equals(antesCorrupcao, Files.readAllBytes(teste)), "versão corrompida: base preservada");

        // base vazia (só o cabeçalho de 4 bytes) e base de 0 bytes
        Path vazio = Paths.get("TP03/teste/vazia.bin");
        Files.write(vazio, new byte[] { 0, 0, 0, 0 });
        int vv = Compressao.comprimirBase(vazio);
        check(Compressao.descomprimirBase(vazio, vv) && Arrays.equals(Files.readAllBytes(vazio), new byte[] { 0, 0, 0, 0 }), "base só com cabeçalho: comprime e restaura");
        Files.write(vazio, new byte[0]);
        vv = Compressao.comprimirBase(vazio);
        check(Compressao.descomprimirBase(vazio, vv) && Files.size(vazio) == 0, "arquivo de 0 bytes: comprime e restaura");

        System.out.println("\n== 5. Regressão TP1/TP2: ordenação e CRUD continuam funcionando ==");
        Reindexacao.reconstruir(teste, arv, 5, le, lg);
        // embaralha a ordem física: atualiza um livro com texto maior (vai pro fim do arquivo)
        arvore = new ArvoreB(arv); lEd = new ListaInvertida(le); lGe = new ListaInvertida(lg);
        try (RandomAccessFile raf = new RandomAccessFile(teste.toString(), "rw")) {
            Livro l = CRUD.read(raf, arvore, 3);
            l.setDescricao(l.getDescricao() + " texto extra para forçar mover o registro para o fim do arquivo ".repeat(5));
            check(CRUD.update(raf, arvore, lEd, lGe, 3, l), "update (registro movido p/ o fim)");
            Livro lido = CRUD.read(raf, arvore, 3);
            check(lido.getDescricao().equals(l.getDescricao()), "read após update devolve o texto novo");
        }
        Ordenacao.main(teste, 3, 20);
        try (RandomAccessFile raf = new RandomAccessFile(teste.toString(), "r")) {
            long pos = 4; int anterior = -1; boolean crescente = true; int n = 0;
            while (pos < raf.length()) {
                raf.seek(pos); int id = raf.readInt(); int tam = raf.readInt();
                if (id <= anterior) crescente = false;
                anterior = id; n++; pos += 8 + tam;
            }
            check(crescente && n > 2000, "ordenação externa: " + n + " registros em ordem crescente de id");
        }
        arvore.close();
        // e comprimir/descomprimir depois de ordenar
        byte[] ordenado = Files.readAllBytes(teste);
        int vo = Compressao.comprimirBase(teste);
        check(Compressao.descomprimirBase(teste, vo) && Arrays.equals(ordenado, Files.readAllBytes(teste)), "base ordenada: comprime e restaura idêntica");

        System.out.println("\n== RESULTADO: " + ok + " OK, " + falhas + " falha(s) ==");
        System.exit(falhas == 0 ? 0 : 1);
    }

    // referência: lê a base inteira (só livros válidos) e conta ocorrências com busca ingênua
    static int contarReferencia(RandomAccessFile raf, String pad, int campo) throws Exception {
        return contarReferencia(raf, pad, campo, false);
    }

    static int contarReferencia(RandomAccessFile raf, String pad, int campo, boolean ignorar) throws Exception {
        int total = 0;
        long pos = 4;
        while (pos < raf.length()) {
            raf.seek(pos);
            int id = raf.readInt(); int tam = raf.readInt();
            Livro l = CRUD.lerRegistro(raf, id);
            pos += 8 + tam;
            if (l == null) continue;
            String[] t = { l.getTitulo(), String.join(", ", l.getAutores()), String.join(", ", l.getGeneros()),
                           l.getDescricao(), l.getEditora(), l.getLingua(), l.getThumbnail() };
            for (int c = 0; c < t.length; c++) if (campo == -1 || campo == c) total += ingenuo(ignorar ? t[c].toLowerCase() : t[c], ignorar ? pad.toLowerCase() : pad).size();
        }
        return total;
    }
}
