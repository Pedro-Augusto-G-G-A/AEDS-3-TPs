package TP02;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;


//guarda os ids dos livros de acordo com uma chave

public class ListaInvertida {

    private final File arquivo;

    // chave -> ids dos livros
    private final TreeMap<String, TreeSet<Integer>> indice;

    public ListaInvertida(File arquivo) throws IOException {
        this.arquivo = arquivo;
        this.indice = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        carregar();
    }

    // carrega os dados do arquivo
    private void carregar() throws IOException {
        indice.clear();

        if (!arquivo.exists()) {
            arquivo.getParentFile().mkdirs();
            arquivo.createNewFile();
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(arquivo))) {
            String linha;

            while ((linha = br.readLine()) != null) {
                if (linha.trim().isEmpty()) continue;

                String[] partes = linha.split("\t", 3);
                if (partes.length < 3) continue;

                String chave = partes[0];
                String idsStr = partes[2];

                TreeSet<Integer> ids = new TreeSet<>();

                if (!idsStr.trim().isEmpty()) {
                    for (String s : idsStr.split(",")) {
                        if (!s.trim().isEmpty()) {
                            ids.add(Integer.parseInt(s.trim()));
                        }
                    }
                }

                indice.put(chave, ids);
            }
        }
    }

    // salva os dados no arquivo
    private void persistir() throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(arquivo, false))) {
            for (Map.Entry<String, TreeSet<Integer>> entrada : indice.entrySet()) {
                StringJoiner sj = new StringJoiner(",");

                for (Integer id : entrada.getValue()) {
                    sj.add(String.valueOf(id));
                }

                bw.write(entrada.getKey() + "\t" + entrada.getValue().size() + "\t" + sj);
                bw.newLine();
            }
        }
    }

    // adiciona um id à chave e salva
    public void insere(String chave, int id) throws IOException {
        if (inserirEmMemoria(chave, id)) {
            persistir();
        }
    }

    // adiciona sem salvar, usado durante a carga inicial
    public void insereSemSalvar(String chave, int id) {
        inserirEmMemoria(chave, id);
    }

    private boolean inserirEmMemoria(String chave, int id) {
        if (chave == null) return false;

        chave = chave.trim();
        if (chave.isEmpty()) return false;

        indice.computeIfAbsent(chave, k -> new TreeSet<>()).add(id);
        return true;
    }

    // remove um id da chave
    public void remove(String chave, int id) throws IOException {
        if (chave == null) return;

        chave = chave.trim();
        if (chave.isEmpty()) return;

        TreeSet<Integer> ids = indice.get(chave);
        if (ids == null) return;

        ids.remove(id);

        if (ids.isEmpty()) {
            indice.remove(chave);
        }

        persistir();
    }

    // busca os ids associados à chave
    public Set<Integer> busca(String chave) {
        if (chave == null) return Collections.emptySet();

        TreeSet<Integer> ids = indice.get(chave.trim());
        return ids == null ? Collections.emptySet() : Collections.unmodifiableSet(ids);
    }

    // retorna as chaves disponíveis
    public Set<String> chaves() {
        return indice.keySet();
    }

    // salva o que está em memória
    public void salvar() throws IOException {
        persistir();
    }

    public void close() throws IOException {
        persistir();
    }
}