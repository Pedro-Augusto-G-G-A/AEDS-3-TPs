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
public class ArvoreB {

    public static final int TAM_HEADER = 4 + 8; // ordem(int) + raiz(long)

    private final int ordem;            // d
    private final int MAX_CHAVES;       // d
    private final int MIN_CHAVES;       // ceil(d/2)
    private final int MAX_FILHOS;       // d+1
    private final int TAMANHO_NO;       // em bytes

    private RandomAccessFile arq;
    private long raiz;

    // Cria o arquivo
    public ArvoreB(File caminho, int ordem) throws IOException {
        if (ordem < 3) throw new IllegalArgumentException("Ordem mínima é 3");
        this.ordem = ordem;
        this.MAX_CHAVES = ordem;
        this.MIN_CHAVES = (ordem - 1) / 2;
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
    public ArvoreB(File caminho) throws IOException {
        if (!caminho.exists()) {
            throw new IOException("Arquivo \"" + caminho + "\" inexistente.");
        }

        arq = new RandomAccessFile(caminho, "rw");

        this.ordem = arq.readInt();
        this.MAX_CHAVES = ordem;
        this.MIN_CHAVES = (ordem - 1) / 2;
        this.MAX_FILHOS = ordem + 1;
        raiz = arq.readLong();

        if (this.ordem < 3) throw new IllegalArgumentException("Ordem mínima é 3");
        // #elementos(4) + d * (8 + 4 + 8 + 8) = 4 + d*28
        this.TAMANHO_NO = 4 + MAX_CHAVES * (8 + 4 + 8 + 8);
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

    /**
     * Atualiza o endereço associado a um id, SEM alterar a estrutura da árvore.
     * Retorna true se achou a chave e reescreveu o nó.
     */
    public boolean atualiza(int id, long novoEndereco) throws IOException {
        if (raiz == -1) return false;
        return atualizaRec(raiz, id, novoEndereco);
    }

    private boolean atualizaRec(long pos, int id, long novoEndereco) throws IOException {
        No no = leNo(pos);
        int i = 0;
        while (i < no.n && id > no.ids[i]) i++;
        if (i < no.n && id == no.ids[i]) {
            no.enderecos[i] = novoEndereco;
            escreveNo(no);
            return true;
        }
        if (no.folha()) return false;
        return atualizaRec(no.filhos[i], id, novoEndereco);
    }

    // ---------- Remoção ----------

    public boolean remove(int id) throws IOException {
        if (raiz == -1) return false;

        boolean removed = removeRec(raiz, id);

        if (removed) {
            No r = leNo(raiz);
            if (r.n == 0) {
                // árvore encolheu
                if (r.folha()) raiz = -1;
                else raiz = r.filhos[0];
                atualizaRaiz();
            }
        }
        return removed;
    }

    private boolean removeRec(long pos, int id) throws IOException {
        No no = leNo(pos);

        // Caso especial: nó vazio com um filho (só a raiz chega nesse estado,
        // após merges sucessivos). Desce direto sem fill.
        if (no.n == 0 && !no.folha()) {
            return removeRec(no.filhos[0], id);
        }

        int i = 0;
        while (i < no.n && id > no.ids[i]) i++;

        // ---- Caso 1 e 2: a chave está neste nó ----
        if (i < no.n && no.ids[i] == id) {
            if (no.folha()) {
                // 1) remove direto da folha
                removeKeyFromNo(no, i);
                escreveNo(no);
                return true;
            } else {
                return removeFromInternal(no, i);
            }
        }

        // ---- Caso 3: desce pro filho ----
        if (no.folha()) return false;

        No filho = leNo(no.filhos[i]);
        if (filho.n <= MIN_CHAVES) {
            // garante que o filho tem MAIS que MIN_CHAVES antes de descer
            fill(no, i);
            // fill pode ter mudado o nó pai (merge); recarrega e reencontra o índice
            no = leNo(pos);
            i = 0;
            while (i < no.n && id > no.ids[i]) i++;
        }
        return removeRec(no.filhos[i], id);
    }

    /** Remove a chave no índice idx do nó (in-place). */
    private void removeKeyFromNo(No no, int idx) {
        for (int j = idx; j < no.n - 1; j++) {
            no.ids[j] = no.ids[j + 1];
            no.enderecos[j] = no.enderecos[j + 1];
        }
        if (!no.folha()) {
            for (int j = idx + 1; j < no.n; j++) {
                no.filhos[j] = no.filhos[j + 1];
            }
            no.filhos[no.n] = -1;
        }
        no.n--;
    }

    /** Chave em nó interno: troca por predecessor/sucessor e desce pra remover. */
    private boolean removeFromInternal(No no, int idx) throws IOException {
        No esq = leNo(no.filhos[idx]);

        if (esq.n > MIN_CHAVES) {
            // 2a) predecessor
            int[] pId = new int[1];
            long[] pEnd = new long[1];
            getPredecessor(no.filhos[idx], pId, pEnd);

            int alvo = pId[0];   // guarda antes de sobrescrever
            no.ids[idx] = pId[0];
            no.enderecos[idx] = pEnd[0];
            escreveNo(no);
            return removeRec(no.filhos[idx], alvo);
        }

        No dir = leNo(no.filhos[idx + 1]);
        if (dir.n > MIN_CHAVES) {
            // 2b) sucessor
            int[] sId = new int[1];
            long[] sEnd = new long[1];
            getSuccessor(no.filhos[idx + 1], sId, sEnd);

            int alvo = sId[0];
            no.ids[idx] = sId[0];
            no.enderecos[idx] = sEnd[0];
            escreveNo(no);
            return removeRec(no.filhos[idx + 1], alvo);
        }

        // 2c) merge dos dois filhos + a chave do meio
        mergeWithRight(no, idx);
        escreveNo(no);
        return removeRec(no.filhos[idx], idx);
    }

    /**
     * Garante que o filho i do pai tenha MAIS que MIN_CHAVES chaves.
     * Tenta emprestar de irmão; se não der, faz merge.
     */
    private void fill(No pai, int i) throws IOException {
        // tenta irmão esquerdo
        if (i > 0) {
            No esq = leNo(pai.filhos[i - 1]);
            if (esq.n > MIN_CHAVES) {
                borrowFromLeft(pai, i);
                return;
            }
        }
        // tenta irmão direito
        if (i < pai.n) {
            No dir = leNo(pai.filhos[i + 1]);
            if (dir.n > MIN_CHAVES) {
                borrowFromRight(pai, i);
                return;
            }
        }
        // não dá pra emprestar: merge
        if (i > 0) mergeWithLeft(pai, i);
        else       mergeWithRight(pai, i);
    }

    /** filho[i] recebe a chave pai.ids[i-1]; esq perde a última chave. */
    private void borrowFromLeft(No pai, int i) throws IOException {
        No esq = leNo(pai.filhos[i - 1]);
        No filho = leNo(pai.filhos[i]);

        // abre espaço no filho (chaves)
        for (int j = filho.n; j > 0; j--) {
            filho.ids[j] = filho.ids[j - 1];
            filho.enderecos[j] = filho.enderecos[j - 1];
        }
        // abre espaço nos filhos (se interno)
        if (!filho.folha()) {
            for (int j = filho.n + 1; j > 0; j--) {
                filho.filhos[j] = filho.filhos[j - 1];
            }
            filho.filhos[0] = esq.filhos[esq.n];
        }

        // chave do pai desce pro início do filho
        filho.ids[0] = pai.ids[i - 1];
        filho.enderecos[0] = pai.enderecos[i - 1];
        filho.n++;

        // última chave do irmão esquerdo sobe
        pai.ids[i - 1] = esq.ids[esq.n - 1];
        pai.enderecos[i - 1] = esq.enderecos[esq.n - 1];
        esq.n--;

        escreveNo(esq);
        escreveNo(filho);
        escreveNo(pai);
    }

    /** filho[i] recebe a chave pai.ids[i]; dir perde a primeira chave. */
    private void borrowFromRight(No pai, int i) throws IOException {
        No dir = leNo(pai.filhos[i + 1]);
        No filho = leNo(pai.filhos[i]);

        // chave do pai desce pro fim do filho
        filho.ids[filho.n] = pai.ids[i];
        filho.enderecos[filho.n] = pai.enderecos[i];
        if (!filho.folha()) {
            filho.filhos[filho.n + 1] = dir.filhos[0];
        }
        filho.n++;

        // primeira chave do irmão direito sobe
        pai.ids[i] = dir.ids[0];
        pai.enderecos[i] = dir.enderecos[0];

        // desloca o irmão direito pra esquerda
        for (int j = 0; j < dir.n - 1; j++) {
            dir.ids[j] = dir.ids[j + 1];
            dir.enderecos[j] = dir.enderecos[j + 1];
        }
        if (!dir.folha()) {
            for (int j = 0; j < dir.n; j++) {
                dir.filhos[j] = dir.filhos[j + 1];
            }
            dir.filhos[dir.n] = -1;
        }
        dir.n--;

        escreveNo(dir);
        escreveNo(filho);
        escreveNo(pai);
    }

    /**
     * Merge de filhos[i], pai.ids[i] e filhos[i+1] -> filhos[i].
     * O pai perde a chave i e o filho i+1.
     */
    private void mergeWithRight(No pai, int i) throws IOException {
        No esq = leNo(pai.filhos[i]);
        No dir = leNo(pai.filhos[i + 1]);
        int esqN = esq.n;

        // chave do meio desce
        esq.ids[esqN] = pai.ids[i];
        esq.enderecos[esqN] = pai.enderecos[i];
        esq.n++;

        // copia chaves do irmão direito
        for (int j = 0; j < dir.n; j++) {
            esq.ids[esq.n] = dir.ids[j];
            esq.enderecos[esq.n] = dir.enderecos[j];
            esq.n++;
        }
        // copia filhos do irmão direito
        if (!esq.folha()) {
            for (int j = 0; j <= dir.n; j++) {
                esq.filhos[esqN + 1 + j] = dir.filhos[j];
            }
        }

        // remove chave i e filho i+1 do pai
        for (int j = i; j < pai.n - 1; j++) {
            pai.ids[j] = pai.ids[j + 1];
            pai.enderecos[j] = pai.enderecos[j + 1];
        }
        for (int j = i + 1; j < pai.n; j++) {
            pai.filhos[j] = pai.filhos[j + 1];
        }
        pai.filhos[pai.n] = -1;
        pai.n--;

        escreveNo(esq);
        escreveNo(pai);
        // o nó 'dir' fica órfão no arquivo — não é referenciado mais.
    }

    private void mergeWithLeft(No pai, int i) throws IOException {
        mergeWithRight(pai, i - 1);
    }

    /** Chave mais à direita da subárvore. */
    private void getPredecessor(long pos, int[] outId, long[] outEnd) throws IOException {
        No no = leNo(pos);
        while (!no.folha()) {
            no = leNo(no.filhos[no.n]);
        }
        outId[0] = no.ids[no.n - 1];
        outEnd[0] = no.enderecos[no.n - 1];
    }

    /** Chave mais à esquerda da subárvore. */
    private void getSuccessor(long pos, int[] outId, long[] outEnd) throws IOException {
        No no = leNo(pos);
        while (!no.folha()) {
            no = leNo(no.filhos[0]);
        }
        outId[0] = no.ids[0];
        outEnd[0] = no.enderecos[0];
    }

    public void close() throws IOException {
        arq.close();
    }
}