package TP02;

import java.io.*;

/**
 * Árvore B em disco que mapeia id -> posição (long) no arquivo binário.
 *
 * A ORDEM d é parametrizada no construtor:
 *   - cada nó tem no máximo d chaves
 *   - cada nó tem no máximo d+1 filhos
 *   - cada nó (exceto a raiz) tem no mínimo ceil(d/2) chaves
 *
 * Layout do arquivo:
 *   [0..3]                    -> int  ordem
 *   [4..11]                   -> long posição da raiz (-1 se vazia)
 *   [12..]                    -> nós de tamanho fixo TAMANHO_NO
 *
 * Layout de um nó:
 *   [int  #elementos]
 *   d * ( [long pos_filho_esq] [int id] [long pos_registro] [long pos_filho_dir] )
 *
 * Convenção de filhos (para casar com o layout "filhos por chave"):
 *   - filhos[0]        = filho mais à esquerda  (antes da chave 0)
 *   - filhos[i+1]      = filho à direita da chave i  (== filho_dir[i])
 *   - filhos[n]        = filho mais à direita   (depois da chave n-1 == filho_dir[n-1])
 *   Guardamos filhos[0..d] (d+1 posições). Os campos filho_esq/filho_dir
 *   por chave são preenchidos de forma redundante com filhos[i]/filhos[i+1]
 *   para respeitar exatamente o formato pedido.
 */
public class ArvoreBMais {

    public static final int TAM_HEADER = 4 + 8; // ordem(int) + raiz(long)

    private final int ordem;            // d
    private final int MAX_CHAVES;       // d
    private final int MIN_CHAVES;       // ceil(d/2)
    private final int MAX_FILHOS;       // d+1
    private final int TAMANHO_NO;       // em bytes

    private RandomAccessFile arq;
    private long raiz;

    // Cria o arquivo
    public ArvoreBMais(File caminho, int ordem) throws IOException {
        if (ordem < 3) throw new IllegalArgumentException("Ordem mínima é 3");
        this.ordem = ordem;
        this.MAX_CHAVES = ordem;
        this.MIN_CHAVES = (ordem + 1) / 2;
        this.MAX_FILHOS = ordem + 1;

        // #elementos(4) + d * (8 + 4 + 8 + 8) = 4 + d*28
        this.TAMANHO_NO = 4 + MAX_CHAVES * (8 + 4 + 8 + 8);

        arq = new RandomAccessFile(caminho, "rw");
        arq.setLength(0);
        raiz = -1;
        arq.writeInt(ordem);
        arq.writeLong(raiz);
    }

    // Lê o arquivo
    public ArvoreBMais(File caminho) throws IOException {
        arq = new RandomAccessFile(caminho, "rw");

        if(!caminho.exists()) {
            throw new IOException("Arquivo \"" + caminho + "\" inexistente.");
        } else {
            this.ordem = arq.readInt();
            this.MAX_CHAVES = ordem;
            this.MIN_CHAVES = (ordem + 1) / 2;
            this.MAX_FILHOS = ordem + 1;
            raiz = arq.readLong();

            if (this.ordem < 3) throw new IllegalArgumentException("Ordem mínima é 3");
            // #elementos(4) + d * (8 + 4 + 8 + 8) = 4 + d*28
            this.TAMANHO_NO = 4 + MAX_CHAVES * (8 + 4 + 8 + 8);
        }
    }

    public int getOrdem() { return ordem; }

    // ---------- Nó em memória ----------
    private static class No {
        int n;
        int[] ids;
        long[] enderecos;
        long[] filhos; // tamanho MAX_FILHOS
        long posicao;

        No(int maxChaves, int maxFilhos) {
            ids = new int[maxChaves];
            enderecos = new long[maxChaves];
            filhos = new long[maxFilhos];
        }

        boolean folha() {
            // É folha se nenhum filho foi setado (todos -1).
            return filhos[0] == -1;
        }
    }

    private No novoNoMemoria() {
        No no = new No(MAX_CHAVES, MAX_FILHOS);
        for (int i = 0; i < MAX_FILHOS; i++) no.filhos[i] = -1;
        return no;
    }

    // ---------- I/O de nó ----------
    private No leNo(long pos) throws IOException {
        No no = novoNoMemoria();
        no.posicao = pos;
        arq.seek(pos);
        no.n = arq.readInt();
        for (int i = 0; i < MAX_CHAVES; i++) {
            long esq = arq.readLong();
            int id = arq.readInt();
            long end = arq.readLong();
            long dir = arq.readLong();
            no.ids[i] = id;
            no.enderecos[i] = end;
            // Reconstrói a lista de filhos a partir dos campos esq/dir.
            // filhos[i] = esq ; filhos[i+1] = dir (o último sobrescreve o próximo).
            if (i == 0) no.filhos[0] = esq;
            no.filhos[i + 1] = dir;
        }
        // Se todos os filhos ficaram -1, é folha.
        return no;
    }

    private void escreveNo(No no) throws IOException {
        arq.seek(no.posicao);
        arq.writeInt(no.n);
        for (int i = 0; i < MAX_CHAVES; i++) {
            long esq = (i < no.n + 1) ? no.filhos[i] : -1;
            long dir = (i < no.n)     ? no.filhos[i + 1] : -1;
            long end = (i < no.n)     ? no.enderecos[i] : 0;
            int  id  = (i < no.n)     ? no.ids[i] : 0;
            // Para posições "vazias" grava zeros/-1 coerentes.
            if (i >= no.n) {
                // campos não usados
                esq = (i == no.n) ? no.filhos[i] : -1;
                dir = -1;
                end = 0;
                id = 0;
            }
            arq.writeLong(esq);
            arq.writeInt(id);
            arq.writeLong(end);
            arq.writeLong(dir);
        }
    }

    private long novoNo() throws IOException {
        long pos = arq.length();
        arq.seek(pos);
        arq.write(new byte[TAMANHO_NO]);
        return pos;
    }

    private void atualizaRaiz() throws IOException {
        arq.seek(4); // depois do int de ordem
        arq.writeLong(raiz);
    }

    // ---------- Inserção ----------
    public void insere(int id, long endereco) throws IOException {
        if (raiz == -1) {
            No no = novoNoMemoria();
            no.n = 1;
            no.ids[0] = id;
            no.enderecos[0] = endereco;
            // filhos continuam -1 (folha)
            no.posicao = novoNo();
            escreveNo(no);
            raiz = no.posicao;
            atualizaRaiz();
            return;
        }

        No r = leNo(raiz);
        if (r.n == MAX_CHAVES) {
            No novaRaiz = novoNoMemoria();
            novaRaiz.n = 0;
            novaRaiz.filhos[0] = raiz;
            novaRaiz.posicao = novoNo();
            escreveNo(novaRaiz);

            splitFilho(novaRaiz, 0, r);
            insereNaoCheio(novaRaiz, id, endereco);

            raiz = novaRaiz.posicao;
            atualizaRaiz();
        } else {
            insereNaoCheio(r, id, endereco);
        }
    }

    /**
     * Divide o filho na posição i do pai. A chave mediana sobe para o pai.
     * Metade esquerda fica no filho, metade direita vai pro novo nó.
     */
    private void splitFilho(No pai, int i, No filho) throws IOException {
        int meio = (filho.n - 1) / 2; // índice da chave que sobe

        No novo = novoNoMemoria();
        novo.n = filho.n - meio - 1;

        // Copia metade direita das chaves
        for (int j = 0; j < novo.n; j++) {
            novo.ids[j] = filho.ids[meio + 1 + j];
            novo.enderecos[j] = filho.enderecos[meio + 1 + j];
        }
        // Copia filhos da metade direita (filhos[meio+1 .. filho.n])
        for (int j = 0; j <= novo.n; j++) {
            novo.filhos[j] = filho.filhos[meio + 1 + j];
        }

        int idMedio = filho.ids[meio];
        long endMedio = filho.enderecos[meio];

        // Trunca o filho
        for (int j = meio; j < MAX_CHAVES; j++) {
            filho.ids[j] = 0;
            filho.enderecos[j] = 0;
        }
        for (int j = meio + 1; j < MAX_FILHOS; j++) {
            filho.filhos[j] = -1;
        }
        filho.n = meio;

        novo.posicao = novoNo();

        // Abre espaço no pai
        for (int j = pai.n; j > i; j--) {
            pai.ids[j] = pai.ids[j - 1];
            pai.enderecos[j] = pai.enderecos[j - 1];
        }
        for (int j = pai.n + 1; j > i + 1; j--) {
            pai.filhos[j] = pai.filhos[j - 1];
        }
        pai.ids[i] = idMedio;
        pai.enderecos[i] = endMedio;
        pai.filhos[i + 1] = novo.posicao;
        pai.n++;

        escreveNo(filho);
        escreveNo(novo);
        escreveNo(pai);
    }

    private void insereNaoCheio(No no, int id, long endereco) throws IOException {
        int i = no.n - 1;

        if (no.folha()) {
            while (i >= 0 && id < no.ids[i]) {
                no.ids[i + 1] = no.ids[i];
                no.enderecos[i + 1] = no.enderecos[i];
                i--;
            }
            no.ids[i + 1] = id;
            no.enderecos[i + 1] = endereco;
            no.n++;
            escreveNo(no);
            return;
        }

        while (i >= 0 && id < no.ids[i]) i--;
        i++;

        No filho = leNo(no.filhos[i]);
        if (filho.n == MAX_CHAVES) {
            splitFilho(no, i, filho);
            if (id > no.ids[i]) i++;
            else if (id == no.ids[i]) return; // duplicado: ignora
            filho = leNo(no.filhos[i]);
        }
        insereNaoCheio(filho, id, endereco);
    }

    // ---------- Busca ----------
    /** Retorna a posição no arquivo de dados, ou -1 se não achar. */
    public long busca(int id) throws IOException {
        if (raiz == -1) return -1;
        return buscaRec(raiz, id);
    }

    private long buscaRec(long pos, int id) throws IOException {
        No no = leNo(pos);
        int i = 0;
        while (i < no.n && id > no.ids[i]) i++;
        if (i < no.n && id == no.ids[i]) return no.enderecos[i];
        if (no.folha()) return -1;
        return buscaRec(no.filhos[i], id);
    }

    public void close() throws IOException {
        arq.close();
    }
}