        package TP01;

        import java.io.IOException;
        import java.io.RandomAccessFile;

    public class CRUD {

    /*parte suporte pro CRUD */

    private static void salvarDados(RandomAccessFile raf, Livro livro) throws IOException {
        raf.writeUTF(livro.getTitulo());

        raf.writeInt(livro.getAutores().length);
        for (String autor : livro.getAutores()) {
            raf.writeUTF(autor);
        }

        raf.writeInt(livro.getPaginas());

        raf.writeInt(livro.getGeneros().length);
        for (String genero : livro.getGeneros()) {
            raf.writeUTF(genero);
        }

        raf.writeUTF(livro.getDescricao());
        raf.writeLong(livro.getDataPublicacao());
        raf.writeUTF(livro.getEditora());
        raf.writeChar(livro.getLingua().charAt(0));
        raf.writeChar(livro.getLingua().charAt(1));
        raf.writeFloat(livro.getMediaReviews());
        raf.writeInt(livro.getQtdReviews());
        raf.writeUTF(livro.getThumbnail());
    }

    /*ideia: passar esse cálculo e o do geradorBin pra classe livro */

    static private int calcTamanho(Livro livro) throws IOException {
        int tamRegistro = 0;
        tamRegistro += 1; // lapide

        tamRegistro += 2 + tamanhoUTF(livro.getTitulo());

        tamRegistro += 4;
        for (String autor : livro.getAutores()) {
            tamRegistro += 2 + tamanhoUTF(autor);
        }

        tamRegistro += 4; // paginas

        tamRegistro += 4;
        for (String genero : livro.getGeneros()) {
            tamRegistro += 2 + tamanhoUTF(genero);
        }

        tamRegistro += 2 + tamanhoUTF(livro.getDescricao());
        tamRegistro += 8; // data
        tamRegistro += 2 + tamanhoUTF(livro.getEditora());
        tamRegistro += 2 + 2; // lingua/idioma
        tamRegistro += 4; // media
        tamRegistro += 4; // qtdReviews
        tamRegistro += 2 + tamanhoUTF(livro.getThumbnail());

        return tamRegistro;
    }

    static private int tamanhoUTF(String texto) {
        if (texto == null) texto = "";
        int qtdBytes = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c >= 0x0001 && c <= 0x007F) {
                qtdBytes += 1;
            } else if (c > 0x07FF) {
                qtdBytes += 3;
            } else {
                qtdBytes += 2;
            }
        }
        return qtdBytes;
    }

    //faz a leitura do último id do csv e retorna qual o prox id que deve ser usado p adicionar um novo livro
    public static int proxId(RandomAccessFile raf) throws IOException {
        raf.seek(0);                    
        int ultimoId = raf.readInt();   
        return ultimoId + 1;
    }


            public static Livro lerRegistro(RandomAccessFile raf, int id) throws IOException {
                boolean lapide = raf.readBoolean();

                String titulo = raf.readUTF();

                int numAutores = raf.readInt();
                String[] autores = new String[numAutores];
                for (int i = 0; i < numAutores; i++) {
                    autores[i] = raf.readUTF();
                }

                int paginas = raf.readInt();

                int numGeneros = raf.readInt();
                String[] generos = new String[numGeneros];
                for (int i = 0; i < numGeneros; i++) {
                    generos[i] = raf.readUTF();
                }

                String descricao = raf.readUTF();
                long dataPublicacao = raf.readLong();
                String editora = raf.readUTF();

                char c1 = raf.readChar();
                char c2 = raf.readChar();
                String lingua = "" + c1 + c2;

                float mediaReviews = raf.readFloat();
                int qtdReviews = raf.readInt();
                String thumbnail = raf.readUTF();

                if (lapide) {
                    return null;
                }

                return new Livro(id, titulo, autores, paginas, generos, descricao,
                                dataPublicacao, editora, lingua, mediaReviews, qtdReviews, thumbnail);
            }

    // ******************CRUD*****************

            public static int create(Livro livro) throws IOException {
        RandomAccessFile raf = new RandomAccessFile("TP01/livros.bin", "rw");

        raf.seek(0);
        int ultimoId = raf.readInt();
        int novoId = ultimoId + 1;

        livro.setId(novoId);

        raf.seek(raf.length());

        int tamanho = calcTamanho
        (livro);
        raf.writeInt(novoId);
        raf.writeInt(tamanho);
        raf.writeBoolean(false);
        salvarDados(raf, livro);

        raf.seek(0);
        raf.writeInt(novoId);

        raf.close();
        return novoId;
    }

    public static Livro read(int idBuscado) throws IOException {
        RandomAccessFile raf = new RandomAccessFile("TP01/livros.bin", "r");
        raf.seek(4);
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
                raf.skipBytes(tamanho);
            }
        }

        raf.close();
        return encontrado;
    }

    public static boolean update(int idBuscado, Livro novoLivro) throws IOException {
        RandomAccessFile raf = new RandomAccessFile("TP01/livros.bin", "rw");
        raf.seek(4); 

        long tamanhoArquivo = raf.length();

        while (raf.getFilePointer() < tamanhoArquivo) {
            int id = raf.readInt();
            int tamanhoAntigo = raf.readInt();
            long posicaoLapide = raf.getFilePointer();

            if (id == idBuscado) {
                int tamanhoNovo = calcTamanho(novoLivro);

                if (tamanhoNovo == tamanhoAntigo) {
                    // se for do mesmo tamanho, sobrescreve o antigo
                    raf.seek(posicaoLapide);
                    raf.writeBoolean(false);
                    salvarDados(raf, novoLivro);
                } else {
                    // se for tamanho diferente, marca o antigo como lápide e adiciona o novo no final
                    raf.seek(posicaoLapide);
                    raf.writeBoolean(true);

                    raf.seek(raf.length());
                    raf.writeInt(idBuscado);
                    raf.writeInt(tamanhoNovo);
                    raf.writeBoolean(false);
                    salvarDados(raf, novoLivro);
                }

                raf.close();
                return true;
            } else {
                raf.skipBytes(tamanhoAntigo);
            }
        }

        raf.close();
        return false;
    }

    public static boolean delete(int idBuscado) throws IOException {
        RandomAccessFile raf = new RandomAccessFile("TP01/livros.bin", "rw");
        raf.seek(4);
        long tamanhoArquivo = raf.length();

        boolean encontrouAlgum = false;

        while (raf.getFilePointer() < tamanhoArquivo) {
            int id = raf.readInt();
            int tamanho = raf.readInt();
            long posicaoLapide = raf.getFilePointer();

            if (id == idBuscado) {
                raf.seek(posicaoLapide);
                raf.writeBoolean(true);
                encontrouAlgum = true;

                raf.seek(posicaoLapide);
                raf.skipBytes(tamanho); 
            } else {
                raf.skipBytes(tamanho);
            }
        }

        raf.close();
        return encontrouAlgum;
    }

}