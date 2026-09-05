package TP01;

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


    //cria um novo registro no final do arquivo e atualiza o cabeçalho com o novo último id

    public static int create(RandomAccessFile raf, Livro livro) throws IOException {
        int novoId = proxId(raf);

        livro.setId(novoId);

        //vai pro final do arquivo, todos os novos registros são inseridos no final
        raf.seek(raf.length());

        int tamanho = livro.getTamanhoEmBytes();
        raf.writeInt(novoId);
        raf.writeInt(tamanho);
        raf.writeBoolean(false);
        salvarDados(raf, livro);

        //atualiza o cabeçalho com o id mais recente usado
        raf.seek(0);
        raf.writeInt(novoId);

        return novoId;
    }

    //busca um registro pelo id, percorrendo sequencialmente
    public static Livro read(RandomAccessFile raf, int idBuscado) throws IOException {
        raf.seek(4); // pula os 4 bytes do cabeçalho (último id)
        long tamanhoArquivo = raf.length();

        Livro encontrado = null;

        while (raf.getFilePointer() < tamanhoArquivo) {
            int id = raf.readInt();
            int tamanho = raf.readInt();

            if (id == idBuscado) {
                Livro livro = lerRegistro(raf, id);
                if (livro != null) {
                    encontrado = livro; 
                }
            } else {
                // não é o id procurado, pula os bytes dele (lápide e dados) sem ler
                raf.skipBytes(tamanho);
            }
        }

        return encontrado;
    }

    /*atualiza um registro existente, se o novo cabe no espaço antigo, apenas sobreescreve.
    se não, marca o antigo como lápide e escreve o novo no final do arquivo */
    public static boolean update(RandomAccessFile raf, int idBuscado, Livro novoLivro) throws IOException {
        raf.seek(4); 

        long tamanhoArquivo = raf.length();

        while (raf.getFilePointer() < tamanhoArquivo) {
            int id = raf.readInt();
            int tamanhoAntigo = raf.readInt();

            if (id == idBuscado) {
                int tamanhoNovo = novoLivro.getTamanhoEmBytes();

                if (tamanhoNovo <= tamanhoAntigo) {
                    // se for do mesmo tamanho, sobrescreve o antigo
                    raf.writeBoolean(false);
                    salvarDados(raf, novoLivro);
                } else {
                    // se for tamanho diferente, marca o antigo como lápide e adiciona o novo no final
                    raf.writeBoolean(true);

                    raf.seek(raf.length());
                    raf.writeInt(idBuscado);
                    raf.writeInt(tamanhoNovo);
                    raf.writeBoolean(false);
                    salvarDados(raf, novoLivro);
                }

                return true;
            } else {
                raf.skipBytes(tamanhoAntigo);
            }
        }

        return false;
    }

    //realiza a exclusão lógica do registro do id buscado
    public static boolean delete(RandomAccessFile raf, int idBuscado) throws IOException {
        raf.seek(4);
        long tamanhoArquivo = raf.length();

        boolean encontrouAlgum = false;

        while (raf.getFilePointer() < tamanhoArquivo) {
            int id = raf.readInt();
            int tamanho = raf.readInt();
            long posicaoLapide = raf.getFilePointer();
            // guarda a posição do byte de lápide pra reescrever depois

            if (id == idBuscado) {
                raf.seek(posicaoLapide);
                raf.writeBoolean(true);
                encontrouAlgum = true;
                //se achou, marca a lápide como true

                // vai para o próximo registro
                raf.seek(posicaoLapide);
                raf.skipBytes(tamanho); 
            } else {
                raf.skipBytes(tamanho);
            }
        }

        return encontrouAlgum;
    }

}