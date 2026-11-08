package TP03;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.PriorityQueue;

/*
    Compressão de Huffman feita byte a byte sobre o arquivo inteiro (livros.bin).

    Como o arquivo é binário (writeInt, writeLong, writeUTF...), tratar tudo como
    uma sequência de bytes dispensa conhecer o formato dos registros: cabeçalho, ids,
    tamanhos, lápides e campos dos livros entram na compressão do mesmo jeito, e a
    descompressão devolve o arquivo idêntico.

    Formato do arquivo comprimido:
        [int  MAGIC "HUF1"]
        [long tamanho original em bytes]
        [int  checksum do arquivo original]
        [int  quantidade de símbolos distintos (0 a 256)]
        n x ( [byte símbolo] [int frequência] )     <- tabela de frequências
        bits dos códigos de Huffman, empacotados (MSB primeiro), com padding no último byte

    Salvamos a tabela de frequências em vez da árvore. Na descompressão a árvore é
    reconstruída com a mesma regra de desempate, então fica igual à original.
*/
public class Huffman {

    private static final int MAGIC = 0x48554631; // "HUF1"

    // ---------------------------------------------------------------- árvore

    // nó da árvore de Huffman
    private static class No implements Comparable<No> {
        final int freq;
        final int desempate; // garante ordem total => árvore determinística
        final int simbolo;   // só vale nas folhas
        final No esq, dir;

        No(int freq, int desempate, int simbolo, No esq, No dir) {
            this.freq = freq;
            this.desempate = desempate;
            this.simbolo = simbolo;
            this.esq = esq;
            this.dir = dir;
        }

        boolean folha() {
            return esq == null && dir == null;
        }

        // menor frequência primeiro; se empatar, menor "desempate" primeiro
        @Override
        public int compareTo(No o) {
            if (freq != o.freq) return Integer.compare(freq, o.freq);
            return Integer.compare(desempate, o.desempate);
        }
    }

    /*
     * Monta a árvore: coloca uma folha por símbolo numa fila de prioridade e vai
     * juntando sempre os dois nós de menor frequência num nó pai, até sobrar um.
     * Desempate: folhas usam o valor do byte. nós internos usam 256, 257...
     * na ordem em que são criados.
     */
    private static No construirArvore(int[] freq) {
        PriorityQueue<No> fila = new PriorityQueue<>();
        for (int s = 0; s < 256; s++) {
            if (freq[s] > 0) {
                fila.add(new No(freq[s], s, s, null, null));
            }
        }
        if (fila.isEmpty()) return null; // arquivo vazio

        int seq = 256;
        while (fila.size() > 1) {
            No a = fila.poll();
            No b = fila.poll();
            fila.add(new No(a.freq + b.freq, seq++, -1, a, b));
        }
        return fila.poll();
    }

    // percorre a árvore: esquerda = bit 0, direita = bit 1; preenche a tabela de códigos
    private static void gerarCodigos(No no, byte[] caminho, int profundidade, byte[][] codigos) {
        if (no.folha()) {
            // caso especial: só existe 1 símbolo distinto (raiz é folha) => código "0"
            int tam = Math.max(profundidade, 1);
            byte[] cod = new byte[tam];
            System.arraycopy(caminho, 0, cod, 0, profundidade); // se profundidade==0, fica {0}
            codigos[no.simbolo] = cod;
            return;
        }
        caminho[profundidade] = 0;
        gerarCodigos(no.esq, caminho, profundidade + 1, codigos);
        caminho[profundidade] = 1;
        gerarCodigos(no.dir, caminho, profundidade + 1, codigos);
    }

    // ----------------------------------- compressão

    public static byte[] comprimir(byte[] dados) throws IOException {
        // 1) conta a frequência de cada byte
        int[] freq = new int[256];
        for (byte b : dados) {
            freq[b & 0xFF]++;
        }

        // 2) árvore + tabela de códigos
        No raiz = construirArvore(freq);
        byte[][] codigos = new byte[256][];
        if (raiz != null) {
            gerarCodigos(raiz, new byte[257], 0, codigos);
        }

        // 3) cabeçalho
        ByteArrayOutputStream bytesSaida = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytesSaida);
        int distintos = 0;
        for (int s = 0; s < 256; s++) if (freq[s] > 0) distintos++;

        out.writeInt(MAGIC);
        out.writeLong(dados.length);
        out.writeInt(Compressao.checksum(dados));
        out.writeInt(distintos);
        for (int s = 0; s < 256; s++) {
            if (freq[s] > 0) {
                out.writeByte(s);
                out.writeInt(freq[s]);
            }
        }

        // 4) corpo: concatena o código de cada byte, empacotando em bytes
        int acumulador = 0; // bits ainda não gravados
        int nBits = 0;
        for (byte b : dados) {
            byte[] cod = codigos[b & 0xFF];
            for (byte bit : cod) {
                acumulador = (acumulador << 1) | bit;
                nBits++;
                if (nBits == 8) {
                    out.writeByte(acumulador);
                    acumulador = 0;
                    nBits = 0;
                }
            }
        }
        if (nBits > 0) { // padding com zeros no último byte
            out.writeByte(acumulador << (8 - nBits));
        }

        out.flush();
        return bytesSaida.toByteArray();
    }

    // ----------------------------------- descompressão

    public static byte[] descomprimir(byte[] comprimido) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(comprimido));

        if (in.readInt() != MAGIC) {
            throw new IOException("Arquivo não é um arquivo Huffman válido");
        }
        long tamanho = in.readLong();
        int checksum = in.readInt();
        int distintos = in.readInt();
        if (tamanho < 0 || tamanho > Integer.MAX_VALUE - 8 || distintos < 0 || distintos > 256) {
            throw new IOException("Cabeçalho Huffman corrompido");
        }

        // lê a tabela de frequências e reconstrói a mesma árvore da compressão
        int[] freq = new int[256];
        for (int i = 0; i < distintos; i++) {
            int simbolo = in.readUnsignedByte();
            freq[simbolo] = in.readInt();
        }

        byte[] saida = new byte[(int) tamanho];
        if (tamanho == 0) {
            return saida;
        }
        No raiz = construirArvore(freq);
        if (raiz == null) {
            throw new IOException("Tabela de frequências vazia para arquivo não vazio");
        }

        // onde começa o corpo (bits): depois do cabeçalho de 20 bytes + 5 bytes por símbolo
        int pos = 4 + 8 + 4 + 4 + distintos * 5;

        // 5) decodifica: anda na árvore bit a bit até chegar numa folha
        int indiceBit = 0; // 0..7 dentro do byte atual (MSB primeiro)
        for (int i = 0; i < saida.length; i++) {
            No no = raiz;
            if (no.folha()) {
                // um único símbolo: cada ocorrência foi gravada como 1 bit
                if (pos >= comprimido.length) throw new IOException("Arquivo Huffman truncado");
                indiceBit++;
                if (indiceBit == 8) { indiceBit = 0; pos++; }
            } else {
                while (!no.folha()) {
                    if (pos >= comprimido.length) throw new IOException("Arquivo Huffman truncado");
                    int bit = (comprimido[pos] >> (7 - indiceBit)) & 1;
                    indiceBit++;
                    if (indiceBit == 8) { indiceBit = 0; pos++; }
                    no = (bit == 0) ? no.esq : no.dir;
                }
            }
            saida[i] = (byte) no.simbolo;
        }

        // 6) confere se o que foi recuperado é o que foi comprimido
        if (Compressao.checksum(saida) != checksum) {
            throw new IOException("Checksum não confere: arquivo Huffman corrompido");
        }
        return saida;
    }
}
