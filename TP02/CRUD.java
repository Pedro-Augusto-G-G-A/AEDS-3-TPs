package TP02;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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


    // Cria um novo registro no final do arquivo, atualiza o cabeçalho, insere na árvore
    // e insere as chaves secundárias (editora / gêneros) nas listas invertidas
    public static int create(RandomAccessFile raf, ArvoreB arvore,
                            ListaInvertida listaEditora, ListaInvertida listaGenero,
                            Livro livro) throws IOException {
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

        // >>> insere no índice B-Tree (busca pelo id)
        arvore.insere(novoId, posicao);
        System.out.println("[Índice] Registro inserido na Árvore B (chave: id).");

        // adiciona o id nas listas invertidas para permitir buscas por editora e gênero
        listaEditora.insere(livro.getEditora(), novoId);
        for (String genero : livro.getGeneros()) {
            listaGenero.insere(genero, novoId);
        }
        System.out.println("[Índice] Registro inserido na Lista Invertida de Editora e na Lista Invertida de Gênero.");

        // atualiza o cabeçalho com o último id usado
        raf.seek(0);
        raf.writeInt(novoId);

        return novoId;
    }

    //busca um registro pelo id usando o índice da Árvore B
    public static Livro read(RandomAccessFile raf, ArvoreB arvore, int idBuscado) throws IOException {
        long posicao = arvore.busca(idBuscado);
        System.out.println("[Índice] Busca realizada via Árvore B (chave: id).");

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
    Se não, marca lápide no antigo, escreve no fim e atualiza a árvore.
    Também resincroniza as listas invertidas: remove as chaves antigas (editora/gêneros
    do registro como estava antes) e insere as novas. */
    public static boolean update(RandomAccessFile raf, ArvoreB arvore,
                                ListaInvertida listaEditora, ListaInvertida listaGenero,
                                int idBuscado, Livro novoLivro) throws IOException {

        long posicao = arvore.busca(idBuscado);
        System.out.println("[Índice] Localização do registro via Árvore B (chave: id).");
        if (posicao == -1) return false; // id nem está na árvore

        // Posição do registro: [id:4][tamanho:4][lapide:1][dados...]
        raf.seek(posicao + 8); // pula id e tamanho, chega na lápide
        boolean lapide = raf.readBoolean();
        if (lapide) return false; // já deletado

        // lê o registro antigo por completo (precisamos da editora/gêneros antigos
        // pra remover das listas invertidas antes de inserir os novos valores)
        raf.seek(posicao + 8);
        Livro livroAntigo = lerRegistro(raf, idBuscado);

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

        // remove os valores antigos das listas invertidas e cadastra os novos
        // Como a lista invertida guarda o id (não a posição), isso não depende de
        // qual dos dois casos acima aconteceu.
        // verifica os dados antigos para remover o id das chaves que não representam mais o livro
        if (livroAntigo != null) {
            listaEditora.remove(livroAntigo.getEditora(), idBuscado);
            for (String genero : livroAntigo.getGeneros()) {
                listaGenero.remove(genero, idBuscado);
            }
        }
        // adiciona o id às listas invertidas usando a editora e os gêneros atualizados
        listaEditora.insere(novoLivro.getEditora(), idBuscado);
        for (String genero : novoLivro.getGeneros()) {
            listaGenero.insere(genero, idBuscado);
        }
        System.out.println("[Índice] Listas Invertidas de Editora e Gênero resincronizadas.");

        return true;
    }

    // realiza a exclusão lógica do registro, remove a chave do índice da árvore
    // e remove o id das listas invertidas (editora / gêneros) em que ele aparecia
    public static boolean delete(RandomAccessFile raf, ArvoreB arvore,
                                ListaInvertida listaEditora, ListaInvertida listaGenero,
                                int idBuscado) throws IOException {
        long posicao = arvore.busca(idBuscado);
        System.out.println("[Índice] Localização do registro via Árvore B (chave: id).");
        if (posicao == -1) return false;               // id nem está na árvore

        // posição do registro: [id:4][tamanho:4][lapide:1][dados...]
        raf.seek(posicao + 8);
        boolean lapide = raf.readBoolean();
        if (lapide) return false;                      // já deletado

        // recupera os dados do livro para identificar a editora e os gêneros associados ao id
        raf.seek(posicao + 8);
        Livro livro = lerRegistro(raf, idBuscado);

        raf.seek(posicao + 8);
        raf.writeBoolean(true);                     // marca lápide

        arvore.remove(idBuscado);                     // remove do índice

        // remove o id das listas invertidas relacionadas ao livro excluído
        if (livro != null) {
            listaEditora.remove(livro.getEditora(), idBuscado);
            for (String genero : livro.getGeneros()) {
                listaGenero.remove(genero, idBuscado);
            }
            System.out.println("[Índice] Id removido da Lista Invertida de Editora e da Lista Invertida de Gênero.");
        }

        return true;
    }

    // ******************BUSCA POR LISTA INVERTIDA*****************

    // retorna apenas os ids que aparecem nos dois conjuntos
    public static Set<Integer> interseccao(Set<Integer> a, Set<Integer> b) {
        TreeSet<Integer> resultado = new TreeSet<>(a);
        resultado.retainAll(b);
        return resultado;
    }

    // reúne os ids dos dois conjuntos sem repetir valores
    public static Set<Integer> uniao(Set<Integer> a, Set<Integer> b) {
        TreeSet<Integer> resultado = new TreeSet<>(a);
        resultado.addAll(b);
        return resultado;
    }

    // transforma os ids encontrados na lista invertida em livros completos
    public static List<Livro> buscaPorIds(RandomAccessFile raf, ArvoreB arvore, Set<Integer> ids) throws IOException {
        List<Livro> resultado = new ArrayList<>();
        for (int id : ids) {
            Livro livro = read(raf, arvore, id);
            // remove o id das listas invertidas relacionadas ao livro excluído
        if (livro != null) {
                resultado.add(livro);
            }
        }
        return resultado;
    }
}