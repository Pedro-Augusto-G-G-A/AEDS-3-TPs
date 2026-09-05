package TP01;

import java.util.Scanner;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Menu {
        
    public static void main(String[] args) throws IOException {
        int opção = -1;
        
        Scanner scanner = new Scanner(System.in);
        Path fonte = Paths.get("TP01/livros.bin");
        RandomAccessFile raf = new RandomAccessFile(fonte.toString(), "rw");

                // Loop do CRUD, roda até o usuário escolher sair (0)

        while (opção != 0) {
            System.out.println("\n===== SISTEMA GERENCIADOR DE BANCO DE DADOS - LIVROS =====");
            System.out.println("1 - CRUD");
            System.out.println("2 - Ordenar");
            System.out.println("0 - Sair");
            System.out.print("Escolha uma opção: ");

            opção = Integer.parseInt(scanner.nextLine());

            switch (opção) {
                case 1:
                    menuCRUD(raf, scanner);
                    break;
                case 2:
                    /*fecha o arquivo antes de ordenar, pois mexe diretamente nele, e reabre depois de terminar */
                    raf.close();
                    Ordenacao.main(fonte);
                    raf = new RandomAccessFile(fonte.toString(), "rw");
                    break;
                case 0:
                    System.out.println("Fechando o programa");
                    break;
                default:
                    System.out.println("Opção inválida");
            }
        }
        raf.close();
        scanner.close();
    }

    //menu do CRUD
    private static void menuCRUD(RandomAccessFile raf, Scanner scanner) throws IOException {
        
        int opção = -1;

        while (opção != 0) {
            System.out.println("\n----- CRUD -----");
            System.out.println("1 - Inserir um novo livro");
            System.out.println("2 - Buscar livro (pelo ID)");
            System.out.println("3 - Atualizar livro");
            System.out.println("4 - Deletar livro");
            System.out.println("0 - Voltar");
            System.out.print("Escolha uma opção: ");

            opção = Integer.parseInt(scanner.nextLine());

            switch (opção) {
                case 1:
                    create(raf, scanner);
                    break;
                case 2:
                    read(raf, scanner); 
                    break;
                case 3:
                    update(raf, scanner);
                    break;
                case 4:
                    delete(raf, scanner); 
                    break;
                case 0:
                    break;
                default:
                    System.out.println("opção inválida");
            }
        }
    }

    // pega os dados de um novo registro pelo terminal e chama CRUD.create para gravar no arquivo
    private static void create(RandomAccessFile raf, Scanner scanner) throws IOException {
        System.out.println("\n--- Inserir novo livro ---");

        System.out.print("Título: ");
        String titulo = scanner.nextLine();

        System.out.print("Autor(es) (separados por vírgula): ");
        String[] autores = scanner.nextLine().split(",");

        //valida a entrada de páginas, repetindo até o usuário digitar um número válido
        int paginas = 0;
        while (true) {
            System.out.print("Número de páginas: ");
            try {
                paginas = Integer.parseInt(scanner.nextLine());
                break;
            } catch (NumberFormatException e) {
                System.out.println("Digite um número válido");
            }
        }

        System.out.print("Gêneros (separados por vírgula): ");
        String[] generos = scanner.nextLine().split(",");

        //ideia: colocar um limite de caracteres aqui
        System.out.print("Descrição (até 500 caracteres): ");
        String descricao = scanner.nextLine();

        System.out.print("Editora: ");
        String editora = scanner.nextLine();

        String lingua;
        while (true) {
            System.out.print("Idioma (2 caracteres, ex: en, pt): ");
            lingua = scanner.nextLine();
            //valida que tem exatamente 2 caracteres
            if (lingua.length() == 2) {
                break;
            }
            System.out.println("O idioma precisa ter exatamente 2 caracteres, digite novamente");
        }

        float mediaReviews = 0;
        while (true) {
            System.out.print("Média das reviews (em ponto): ");
            //valida se é um número decimal
            try {
                mediaReviews = Float.parseFloat(scanner.nextLine());
                break;
            } catch (NumberFormatException e) {
                System.out.println("Digite um número válido (ex: 4.5)");
            }
        }

        int qtdReviews = 0;
        while (true) {
            System.out.print("Quantidade de reviews: ");
            //valida como um número inteiro
            try {
                qtdReviews = Integer.parseInt(scanner.nextLine());
                break;
            } catch (NumberFormatException e) {
                System.out.println("Digite um número válido");
            }
        }

        System.out.print("Thumbnail (URL): ");
        String thumbnail = scanner.nextLine();

        Livro livro = null;
        while (livro == null) {
            //*******************************************************olhar aqui
            System.out.print("Data de publicação (yyyy/MM/dd, yyyy-MM-dd, yyyy-MM, yyyy ou Unknown): ");
            String dataPublicacao = scanner.nextLine();

            try {
                livro = new Livro(-1, titulo, autores, paginas, generos, descricao,
                                dataPublicacao, editora, lingua, mediaReviews, qtdReviews, thumbnail);
            } catch (Exception e) {
                System.out.println("Data inválida, tente novamente (" + e.getMessage() + ")");
            }
        }
        // id é gerado dentro do CRUD.create, não é definido pelo usuário
        int idGerado = CRUD.create(raf, livro);
        System.out.println("Livro inserido! ID: " + idGerado);
    }

    //busca e exibe o registro de um livro pelo id, se existir
    private static void read(RandomAccessFile raf, Scanner scanner) throws IOException {
        System.out.println("\n--- Buscar livro ---");
        System.out.print("Id do livro: ");
        int id = Integer.parseInt(scanner.nextLine());

        Livro livro = CRUD.read(raf, id);

        if (livro == null) {
            System.out.println("Livro não encontrado (ou deletado)");
        } else {
            System.out.println(livro);
        }
    }

//atualiza um livro existente, permitindo manter os valores atuais ao apertar enter em cada campo

    private static void update(RandomAccessFile raf, Scanner scanner) throws IOException {
        System.out.println("\n--- Atualizar livro ---");
        System.out.print("Id do livro: ");
        int id = Integer.parseInt(scanner.nextLine());

        Livro livroAtual = CRUD.read(raf, id);
        if (livroAtual == null) {
            System.out.println("Livro não encontrado");
            return;
        }

        System.out.println("Dados atuais:");
        System.out.println(livroAtual);
        System.out.println("\nDigite os novos dados (Enter mantém o valor atual em todos os campos):");

        System.out.print("Título [" + livroAtual.getTitulo() + "]: ");
        String tituloInput = scanner.nextLine();
        String titulo = tituloInput.isEmpty() ? livroAtual.getTitulo() : tituloInput;

        String autoresAtuais = String.join(",", livroAtual.getAutores());
        System.out.print("Autores separados por vírgula [" + autoresAtuais + "]: ");
        String autoresInput = scanner.nextLine();
        String[] autores = autoresInput.isEmpty() ? livroAtual.getAutores() : autoresInput.split(",");

        //aqui verifica os números preenchidos para ver se são válidos
        int paginas = livroAtual.getPaginas();
        while (true) {
            System.out.print("Número de páginas [" + livroAtual.getPaginas() + "]: ");
            String input = scanner.nextLine();
            if (input.isEmpty()) {
                break; 
            }
            try {
                paginas = Integer.parseInt(input);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Digite um número válido");
            }
        }

        String generosAtuais = String.join(",", livroAtual.getGeneros());
        System.out.print("Gêneros separados por vírgula [" + generosAtuais + "]: ");
        String generosInput = scanner.nextLine();
        String[] generos = generosInput.isEmpty() ? livroAtual.getGeneros() : generosInput.split(",");

        //valida o tamanho máximo da descrição apenas se o usuário digitar de novo
        String descricao = livroAtual.getDescricao();
        while (true) {
            System.out.print("Descrição (até 500 caracteres) [manter atual]: ");
            String input = scanner.nextLine();
            if (input.isEmpty()) {
                break;
            }
            if (input.length() <= 550) {
                descricao = input;
                break;
            }
            System.out.println("Descrição muito longa (" + input.length() + " caracteres)");
        }

        System.out.print("Editora [" + livroAtual.getEditora() + "]: ");
        String editoraInput = scanner.nextLine();
        String editora = editoraInput.isEmpty() ? livroAtual.getEditora() : editoraInput;

        //se digitar algo novo, verifica se possui 2 caracteres
        String lingua = livroAtual.getLingua();
        while (true) {
            System.out.print("Língua (2 caracteres) [" + livroAtual.getLingua() + "]: ");
            String input = scanner.nextLine();
            if (input.isEmpty()) {
                break;
            }
            if (input.length() == 2) {
                lingua = input;
                break;
            }
            System.out.println("A língua precisa ter exatamente 2 caracteres");
        }

        float mediaReviews = livroAtual.getMediaReviews();
        while (true) {
            System.out.print("Média das reviews [" + livroAtual.getMediaReviews() + "]: ");
            String input = scanner.nextLine();
            if (input.isEmpty()) {
                break;
            }
            try {
                mediaReviews = Float.parseFloat(input);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Digite um número válido (ex: 4.5)");
            }
        }

        int qtdReviews = livroAtual.getQtdReviews();
        while (true) {
            System.out.print("Quantidade de reviews [" + livroAtual.getQtdReviews() + "]: ");
            String input = scanner.nextLine();
            if (input.isEmpty()) {
                break;
            }
            try {
                qtdReviews = Integer.parseInt(input);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Digite um número válido");
            }
        }

        System.out.print("Thumbnail [" + livroAtual.getThumbnail() + "]: ");
        String thumbnailInput = scanner.nextLine();
        String thumbnail = thumbnailInput.isEmpty() ? livroAtual.getThumbnail() : thumbnailInput;

        Livro livroNovo = null;
        while (livroNovo == null) {
            System.out.print("Data de publicação (yyyy-MM-dd, yyyy-MM, yyyy ou Unknown) [manter atual]: ");
            String dataInput = scanner.nextLine();
            String dataPublicacao = dataInput.isEmpty()
                ? livroAtual.DataPublicacaoToString(livroAtual.getDataPublicacao())
                : dataInput;

            try {
                livroNovo = new Livro(id, titulo, autores, paginas, generos, descricao,
                                    dataPublicacao, editora, lingua, mediaReviews, qtdReviews, thumbnail);
            } catch (Exception e) {
                System.out.println("Data inválida, tente novamente (" + e.getMessage() + ")");
            }
        }

        boolean atualizou = CRUD.update(raf, id, livroNovo);
        if (atualizou) {
            System.out.println("Livro atualizado com sucesso!");
        } else {
            System.out.println("Livro não encontrado");
        }
    }

    // deleta logicamente um livro pelo id digitado
    private static void delete(RandomAccessFile raf, Scanner scanner) throws IOException {
        System.out.println("\n--- Deletar livro ---");
        System.out.print("Id do livro: ");
        int id = Integer.parseInt(scanner.nextLine());

        boolean deletou = CRUD.delete(raf, id);

        if (deletou) {
            System.out.println("Livro deletado com sucesso");
        } else {
            System.out.println("Livro não encontrado");
        }
    }
}