package TP03;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

/*
 * Compressão LZW feita BYTE A BYTE sobre o arquivo inteiro (livros.bin).
 *
 * DICIONÁRIO INICIAL 
 *   - códigos 0..255 -> as 256 sequências de 1 byte (do byte 0x00 até o 0xFF).
 *     Como o arquivo é binário e pode conter qualquer valor de byte, o dicionário
 *     inicial precisa ter os 256 valores, senão algum byte não teria código.
 *   - códigos 256... -> sequências novas, criadas durante a compressão.
 *
 * CÓDIGOS DE TAMANHO FIXO: cada código é gravado com 12 bits, então o dicionário
 * comporta no máximo 4096 entradas (0 a 4095). Quando enche, simplesmente PARAMOS de
 * cadastrar sequências novas e seguimos usando as que já existem (o compressor e o
 * descompressor seguem a mesma regra, por isso continuam sincronizados).
 *
 * Formato do arquivo comprimido:
 *   [int  MAGIC "LZW1"]
 *   [long tamanho original em bytes]
 *   [int  checksum do arquivo original]
 *   códigos de 12 bits, um atrás do outro (MSB primeiro), último byte com padding
 */
public class LZW {

    private static final int MAGIC = 0x4C5A5732; // "LZW2" (versão de código fixo)

    private static final int LARGURA = 12;              // bits por código
    private static final int MAX_CODIGOS = 1 << LARGURA; // 4096 entradas no dicionário
    private static final int PRIMEIRO_CODIGO = 256;      // 0..255 já são os bytes simples

    // ------------------------------------------------------------ compressão

    public static byte[] comprimir(byte[] dados) throws IOException {
        ByteArrayOutputStream bytesSaida = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytesSaida);
        out.writeInt(MAGIC);
        out.writeLong(dados.length);
        out.writeInt(Compressao.checksum(dados));

        EscritorBits escritor = new EscritorBits(out);

        // dicionário das sequências NOVAS: (código do prefixo, próximo byte) -> código
        // (as 256 iniciais não precisam estar no mapa: o código de um byte é o próprio byte)
        HashMap<Integer, Integer> dicionario = new HashMap<>();
        int proximo = PRIMEIRO_CODIGO; // próximo código livre

        int w = -1; // código da maior sequência já reconhecida (-1 = ainda não leu nada)

        for (byte b : dados) {
            int c = b & 0xFF;

            if (w == -1) { // primeiro byte: a sequência atual é só ele
                w = c;
                continue;
            }

            int chave = (w << 8) | c; // identifica a sequência "w seguida de c"
            Integer achou = dicionario.get(chave);
            if (achou != null) {
                w = achou; // w+c já existe: continua estendendo a sequência
                continue;
            }

            // w+c é inédita: emite o código de w, cadastra w+c (se ainda couber) e recomeça de c
            escritor.escrever(w);
            if (proximo < MAX_CODIGOS) {
                dicionario.put(chave, proximo++);
            }
            w = c;
        }

        if (w != -1) { // emite o que sobrou
            escritor.escrever(w);
        }
        escritor.finalizar();

        out.flush();
        return bytesSaida.toByteArray();
    }

    // ---------------------------------------------------------- descompressão

    /*
     * O descompressor reconstrói o mesmo dicionário lendo os códigos. Ele anda "1 entrada
     * atrás" do compressor: só descobre o último byte de uma entrada ao ler o código seguinte.
     * Cada entrada nova é guardada como (código do prefixo, último byte).
     */
    public static byte[] descomprimir(byte[] comprimido) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(comprimido));

        if (in.readInt() != MAGIC) {
            throw new IOException("Arquivo não é um arquivo LZW válido");
        }
        long tamanho = in.readLong();
        int checksum = in.readInt();
        if (tamanho < 0 || tamanho > Integer.MAX_VALUE - 8) {
            throw new IOException("Cabeçalho LZW corrompido");
        }

        byte[] saida = new byte[(int) tamanho];
        if (tamanho > 0) {
            int[] prefixo = new int[MAX_CODIGOS];      // prefixo[codigo] = código da sequência sem o último byte
            byte[] sufixo = new byte[MAX_CODIGOS];     // sufixo[codigo] = último byte da sequência
            byte[] pilha = new byte[MAX_CODIGOS + 1];  // para montar uma sequência de trás pra frente

            LeitorBits leitor = new LeitorBits(comprimido, 4 + 8 + 4);

            int proximo = PRIMEIRO_CODIGO;
            int anterior = -1; // último código lido (-1 = nenhum ainda)
            int pos = 0;       // quantos bytes já foram escritos em saida

            while (pos < saida.length) {
                int codigo = leitor.ler();

                if (anterior == -1) {
                    // primeiro código: tem que ser um byte simples
                    if (codigo >= PRIMEIRO_CODIGO) throw new IOException("Arquivo LZW corrompido (código inválido)");
                } else if (proximo < MAX_CODIGOS) {
                    // cadastra a entrada que o compressor criou depois de emitir "anterior":
                    //   sequência(anterior) + primeiro byte da sequência atual
                    int primeiroByte;
                    if (codigo < proximo) {
                        primeiroByte = primeiroByte(codigo, prefixo);
                    } else if (codigo == proximo) {
                        // caso especial (padrão K w K w K): o código lido é justamente a entrada que
                        // ainda não existe; ela é sequência(anterior) + primeiro byte de sequência(anterior)
                        primeiroByte = primeiroByte(anterior, prefixo);
                    } else {
                        throw new IOException("Arquivo LZW corrompido (código fora do dicionário)");
                    }
                    prefixo[proximo] = anterior;
                    sufixo[proximo] = (byte) primeiroByte;
                    proximo++;
                } else if (codigo >= proximo) {
                    throw new IOException("Arquivo LZW corrompido (código fora do dicionário)");
                }

                // escreve a sequência do código lido (montada de trás pra frente e invertida)
                int n = 0;
                int c = codigo;
                while (c >= PRIMEIRO_CODIGO) {
                    pilha[n++] = sufixo[c];
                    c = prefixo[c];
                }
                pilha[n++] = (byte) c;
                if (pos + n > saida.length) throw new IOException("Arquivo LZW corrompido (saída maior que o original)");
                for (int i = n - 1; i >= 0; i--) {
                    saida[pos++] = pilha[i];
                }

                anterior = codigo;
            }
        }

        if (Compressao.checksum(saida) != checksum) {
            throw new IOException("Checksum não confere: arquivo LZW corrompido");
        }
        return saida;
    }

    // primeiro byte da sequência de um código (desce a cadeia de prefixos até o byte simples)
    private static int primeiroByte(int codigo, int[] prefixo) {
        while (codigo >= PRIMEIRO_CODIGO) {
            codigo = prefixo[codigo];
        }
        return codigo;
    }

    // ------------------------------------------------- leitura/escrita de bits

    // grava códigos de 12 bits, encaixando um atrás do outro (MSB primeiro)
    private static class EscritorBits {
        private final DataOutputStream out;
        private int buffer = 0; // bits pendentes (menos de 8 depois de cada escrita)
        private int nBits = 0;

        EscritorBits(DataOutputStream out) {
            this.out = out;
        }

        void escrever(int codigo) throws IOException {
            buffer = (buffer << LARGURA) | codigo;
            nBits += LARGURA;
            while (nBits >= 8) { // sempre que juntar um byte inteiro, grava
                out.writeByte((buffer >> (nBits - 8)) & 0xFF);
                nBits -= 8;
            }
            buffer &= (1 << nBits) - 1; // descarta os bits já gravados
        }

        // completa o último byte com zeros
        void finalizar() throws IOException {
            if (nBits > 0) {
                out.writeByte((buffer << (8 - nBits)) & 0xFF);
                nBits = 0;
                buffer = 0;
            }
        }
    }

    // lê códigos de 12 bits de um vetor de bytes
    private static class LeitorBits {
        private final byte[] dados;
        private int pos;
        private int buffer = 0;
        private int nBits = 0;

        LeitorBits(byte[] dados, int posInicial) {
            this.dados = dados;
            this.pos = posInicial;
        }

        int ler() throws IOException {
            while (nBits < LARGURA) {
                if (pos >= dados.length) throw new IOException("Arquivo LZW truncado");
                buffer = (buffer << 8) | (dados[pos++] & 0xFF);
                nBits += 8;
            }
            int codigo = (buffer >> (nBits - LARGURA)) & (MAX_CODIGOS - 1);
            nBits -= LARGURA;
            buffer &= (1 << nBits) - 1;
            return codigo;
        }
    }
}
