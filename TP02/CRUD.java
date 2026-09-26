package TP02;

import java.io.IOException;
import java.io.RandomAccessFile;

public class CRUD {

    /*parte suporte pro CRUD */

     // escreve todos os campos do livro no arquivo
    public static void salvarDados(RandomAccessFile raf, Livro livro) throws IOException {
        raf.writeUTF(livro.getTitulo());

        // salva a quantidade de autores antes da lista, salvando o vetor antes de escrever
        raf.writeInt(livro.getAutores().length);
        for (String autor : livro.getAutores()) {
            raf.writeUTF(autor);
        }

        raf.writeInt(livro.getPaginas());

        //mesma lógica dos autores
        raf.writeInt(livro.getGeneros().length);
        for (String genero : livro.getGeneros()) {
            raf.writeUTF(genero);
        }

        raf.writeUTF(livro.getDescricao());
        raf.writeLong(livro.getDataPublicacao());
        raf.writeUTF(livro.getEditora());
            
        //o idioma é salvo como 2 chars separados ao invés de uma string
        raf.writeChar(livro.getLingua().charAt(0));
        raf.writeChar(livro.getLingua().charAt(1));
        raf.writeFloat(livro.getMediaReviews());
        raf.writeInt(livro.getQtdReviews());
        raf.writeUTF(livro.getThumbnail());
    }

    //faz a leitura do último id do csv e retorna qual o próximo id que deve ser usado para adicionar um novo livro
    public static int proxId(RandomAccessFile raf) throws IOException {
        raf.seek(0);                    
        int ultimoId = raf.readInt();   
        return ultimoId + 1;
    }

    public static Livro lerRegistro(RandomAccessFile raf, int id) throws IOException {
        //lápide indica se o registro foi deletado sem remover fisicamente do arquivo
        boolean lapide = raf.readBoolean();

        if (lapide) {
            return null;
        }

        String titulo = raf.readUTF();

        //lê a quantidade de autores salva antes, depois lê cada autor
        int numAutores = raf.readInt();
        String[] autores = new String[numAutores];
        for (int i = 0; i < numAutores; i++) {
            autores[i] = raf.readUTF();
        }

        int paginas = raf.readInt();

        //mesma lógica pros gêneros
        int numGeneros = raf.readInt();
        String[] generos = new String[numGeneros];
        for (int i = 0; i < numGeneros; i++) {
            generos[i] = raf.readUTF();
        }

        String descricao = raf.readUTF();
        long dataPublicacao = raf.readLong();
        String editora = raf.readUTF();

        //reconstrói a string dos idiomas a partir dos chars gravados
        char c1 = raf.readChar();
        char c2 = raf.readChar();
        String lingua = "" + c1 + c2;

        float mediaReviews = raf.readFloat();
        int qtdReviews = raf.readInt();
        String thumbnail = raf.readUTF();

        return new Livro(id, titulo, autores, paginas, generos, descricao,
                        dataPublicacao, editora, lingua, mediaReviews, qtdReviews, thumbnail);
    }

    // ******************CRUD*****************


    // Cria um novo registro no final do arquivo, atualiza o cabeçalho e insere no índice
    public static int create(RandomAccessFile raf, ArvoreBMais arvore, Livro livro) throws IOException {
        int novoId = proxId(raf);
        livro.setId(novoId);

        // vai pro final do arquivo — novos registros sempre entram no fim
        raf.seek(raf.length());

        // >>> posição do início do registro (onde o id vai ser escrito)
        long posicao = raf.getFilePointer();

        int tamanho = livro.getTamanhoEmBytes();
        raf.writeInt(novoId);
        raf.writeInt(tamanho);
        raf.writeBoolean(false);
        salvarDados(raf, livro);

        // >>> insere no índice B-Tree
        arvore.insere(novoId, posicao);

        // atualiza o cabeçalho com o último id usado
        raf.seek(0);
        raf.writeInt(novoId);

        return novoId;
    }

    //busca um registro pelo id usando o índice da Árvore B
    public static Livro read(RandomAccessFile raf, ArvoreBMais arvore, int idBuscado) throws IOException {
        long posicao = arvore.busca(idBuscado);

        // -1 => id não está no índice
        if (posicao == -1) {
            return null;
        }

        // A posição aponta pro começo do registro (campo id).
        // Pula id (4 bytes) + tamanho (4 bytes) pra chegar na lápide.
        raf.seek(posicao + 8);

        // lerRegistro já checa a lápide e devolve null se deletado.
        return lerRegistro(raf, idBuscado);
    }

    /* Atualiza um registro existente. Usa a árvore B pra localizar a posição atual.
    Se o novo conteúdo couber no espaço antigo, sobrescreve no lugar.
    Se não, marca lápide no antigo, escreve no fim e atualiza a árvore. */
    public static boolean update(RandomAccessFile raf, ArvoreBMais arvore,
                                int idBuscado, Livro novoLivro) throws IOException {

        long posicao = arvore.busca(idBuscado);
        if (posicao == -1) return false; // id nem está na árvore

        // Posição do registro: [id:4][tamanho:4][lapide:1][dados...]
        raf.seek(posicao + 8); // pula id e tamanho, chega na lápide
        boolean lapide = raf.readBoolean();
        if (lapide) return false; // já deletado

        // Volta pra ler o tamanho alocado
        raf.seek(posicao + 4);
        int tamanhoAntigo = raf.readInt();

        int tamanhoNovo = novoLivro.getTamanhoEmBytes();

        if (tamanhoNovo <= tamanhoAntigo) {
            // ---------- cabe no espaço antigo: sobrescreve no lugar ----------
            raf.seek(posicao + 8);        // posição da lápide
            raf.writeBoolean(false);      // reafirma que não está deletado
            salvarDados(raf, novoLivro);
            // árvore continua apontando pro mesmo lugar: nada a fazer
        } else {
            // ---------- não cabe: marca lápide no antigo e escreve no fim ----------
            raf.seek(posicao + 8);
            raf.writeBoolean(true);

            raf.seek(raf.length());
            long novaPosicao = raf.getFilePointer();   // <-- posição do novo registro
            raf.writeInt(idBuscado);
            raf.writeInt(tamanhoNovo);
            raf.writeBoolean(false);
            salvarDados(raf, novoLivro);

            // Atualiza o índice: o id agora mora em novaPosicao
            arvore.atualiza(idBuscado, novaPosicao);
        }

        return true;
    }

    // realiza a exclusão lógica do registro e remove a chave do índice
    public static boolean delete(RandomAccessFile raf, ArvoreBMais arvore, int idBuscado) throws IOException {
        long posicao = arvore.busca(idBuscado);
        if (posicao == -1) return false;               // id nem está na árvore

        // posição do registro: [id:4][tamanho:4][lapide:1][dados...]
        raf.seek(posicao + 8);
        boolean lapide = raf.readBoolean();
        if (lapide) return false;                      // já deletado

        raf.seek(posicao + 8);
        raf.writeBoolean(true);                     // marca lápide

        arvore.remove(idBuscado);                     // remove do índice

        return true;
    }
}