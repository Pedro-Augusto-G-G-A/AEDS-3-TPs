package TP03;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/*
 * Reconstrói os índices do TP2 (Árvore B e listas invertidas de editora e gênero)
 * varrendo o arquivo de dados.
 *
 * Quando é necessário no TP3:
 *  - depois de descomprimir: o arquivo restaurado pode ser uma versão ANTIGA da base
 *    (inserções/alterações/exclusões feitas depois da compressão não estariam nele), e aí
 *    as posições guardadas na Árvore B e as listas invertidas ficariam desatualizadas;
 *  - na primeira execução, se os arquivos de índice não existirem.
 *
 * Só entram nos índices os registros com lápide = false (não excluídos).
 */
public class Reindexacao {

    public static void reconstruir(Path fonte, File arqArvore, int ordem,
                                   File arqListaEditora, File arqListaGenero) throws IOException {
        // apaga as listas antigas para não misturar com dados desatualizados
        if (arqListaEditora.exists()) arqListaEditora.delete();
        if (arqListaGenero.exists()) arqListaGenero.delete();

        ArvoreB arvore = new ArvoreB(arqArvore, ordem); // este construtor recria o arquivo do zero
        ListaInvertida listaEditora = new ListaInvertida(arqListaEditora);
        ListaInvertida listaGenero = new ListaInvertida(arqListaGenero);

        try (RandomAccessFile raf = new RandomAccessFile(fonte.toString(), "r")) {
            long tamanhoArquivo = raf.length();
            long posicao = 4; // pula o cabeçalho (último id)

            while (posicao < tamanhoArquivo) {
                raf.seek(posicao);
                int id = raf.readInt();
                int tamanho = raf.readInt();

                Livro livro = CRUD.lerRegistro(raf, id); // devolve null se a lápide estiver marcada
                if (livro != null) {
                    arvore.insere(id, posicao);
                    listaEditora.insereSemSalvar(livro.getEditora(), id);
                    for (String genero : livro.getGeneros()) {
                        listaGenero.insereSemSalvar(genero, id);
                    }
                }
                posicao += 4 + 4 + tamanho; // id + tamanho + corpo (lápide + dados)
            }
        }

        listaEditora.salvar();
        listaGenero.salvar();
        arvore.close();
    }
}
