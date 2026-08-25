    package TP01;

    import java.io.IOException;
    import java.io.RandomAccessFile;

public class CRUD {

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

        public static Livro read(int idBuscado) throws IOException {
            RandomAccessFile raf = new RandomAccessFile("TP01/livros.bin", "r");

            raf.seek(4); // pula o cabeçalho

            long tamanhoArquivo = raf.length();

            while (raf.getFilePointer() < tamanhoArquivo) {
                int id = raf.readInt();
                int tamanho = raf.readInt();

                if (id == idBuscado) {
                    Livro livro = lerRegistro(raf, id);
                    raf.close();
                    return livro;
                } else {
                    raf.skipBytes(tamanho);
                }
            }

            raf.close();
            return null;
        }

        /*parte pro update */


        public static boolean delete(int idBuscado) throws IOException {
        RandomAccessFile raf = new RandomAccessFile("TP01/livros.bin", "rw");

        raf.seek(4); // pula o cabeçalho

        long tamanhoArquivo = raf.length();

        while (raf.getFilePointer() < tamanhoArquivo) {
            int id = raf.readInt();
            int tamanho = raf.readInt();
            long posicaoLapide = raf.getFilePointer(); // guarda onde a lápide do registro tá

            if (id == idBuscado) {
                raf.seek(posicaoLapide);      // volta pro início do registro (lápide)
                raf.writeBoolean(true);       //  deletado
                raf.close();
                return true;
            } else {
                raf.skipBytes(tamanho); // próx registro
            }
        }

        raf.close();
        return false; // não achou
    }

    /*menu teste (***********CORRIGIR*************)*/
    public static void main(String[] args) throws IOException {

        Livro livro = CRUD.read(2008);

        if (livro == null) {
            System.out.println("Livro não encontrado (ou foi deletado).");
        } else {
            System.out.println(livro);
        }

        boolean deletou = CRUD.delete(2008); 
        System.out.println("Deletado? " + deletou);

        livro = CRUD.read(2008); // tenta ler dnv
        if (livro == null) {
            System.out.println("Livro não encontrado (ou foi deletado) — confirma que o delete funcionou!");
        } else {
            System.out.println("Ops, ainda encontrou:");
            System.out.println(livro);
        }
    }
}