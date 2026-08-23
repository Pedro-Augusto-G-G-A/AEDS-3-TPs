# AEDS-3-TPs - Banco de Dados Binário de Livros

## Visão Geral do Projeto

Este projeto implementa um sistema de banco de dados de livros em arquivo binário usando Java. É composto por dois componentes principais que trabalham juntos para fornecer armazenamento e manipulação eficiente de dados:

1. **Conversor CSV para Binário** (`GeradorBin.java`) - Lê dados de livros de um arquivo CSV e os converte para um formato binário personalizado
2. **Gerenciador de Arquivo Binário** (`ManipuladorBin.java`) - Fornece operações CRUD (Create, Read, Update, Delete) e ordenação no arquivo binário

## Estrutura do Projeto

```
TP01/
├── GeradorBin.java          # Conversor CSV para Binário
├── ManipuladorBin.java      # Gerenciador do arquivo binário com CRUD e ordenação
├── Livro.java               # Classe entidade Livro
└── Dataset/
    └── Books.csv            # Conjunto de dados fonte (informações dos livros)
```

## Formato do Arquivo Binário

O arquivo binário (`livros.bin`) segue um formato personalizado otimizado para acesso eficiente aos dados:

```
[INT: total_registros]
[
  [INT: id]              // ID do livro (4 bytes)
  [BOOL: lapide]         // Marcador de exclusão (1 byte): true = deletado, false = ativo
  [INT: tam_titulo] [CHAR*: titulo]                   // String de tamanho variável
  [INT: qtd_autor][INT: tam_autor] [CHAR*: autor]     // Array de Strings de tamanhos variáveis
  [INT: paginas]                                      // Número de páginas (4 bytes)
  [INT: qtd_genero] [INT: tam_genero] [CHAR*: genero] // Array de Strings de tamanhos variáveis
  [INT: tam_desc] [CHAR*: descricao]                  // String de tamanho variável
  [LONG: data_publicacao] // Data de publicação em milissegundos desde 1970 (8 bytes)
  [INT: tam_editora] [CHAR*: editora]                 // String de tamanho variável
  [CHAR*: lingua] [CHAR*: lingua]                     // String de tamanho fixo (2 char)
  [FLOAT: media_reviews]                              // Float para media das reviews
  [INT: qtd_reviews]                                  // INT para qtd das reviews
  [INT: tam_thumbnail] [CHAR*: thumbnail]             // String de tamanho variável
] × N
```

## Funcionalidades

### 1. Conversor CSV para Binário (`GeradorBin.java`)
- Lê dados do arquivo `Books.csv`
    - `Books.csv` Vêm originalmente de https://www.kaggle.com/datasets/madankhatri123h/books-dataset
        - O livro "Agatha Christie: The Queen of Murder Mysteries (Biography of Her Life)" estava com uma data errada de "101-01-01". Isso nos levou a perceber que o livro é ficticio, não existe, então essa remoção foi a única modificação feita ao csv original
- Faz o parsing do CSV com tratamento adequado de campos entre aspas (incluindo vírgulas dentro das aspas)
- Converte e armazena os dados no formato binário personalizado
- Ignora a linha de cabeçalho do CSV

### 2. Gerenciador de Arquivo Binário (`ManipuladorBin.java`)
- **Create (Criar)**: Adiciona novos livros ao banco de dados
- **Read (Ler)**: Recupera informações de um livro pelo ID
- **Update (Atualizar)**: Modifica registros de livros existentes
- **Delete (Deletar)**: Exclusão lógica usando marcador de lápide (soft delete)
- **Sort (Ordenar)**: Ordena os registros por diferentes critérios (ID, título, páginas, etc.)

## Tecnologias Utilizadas

- **Java 8+** - Linguagem de programação
- **java.io** - Para operações de entrada e saída de arquivos
- **java.util** - Para estruturas de dados e utilitários

## Como Usar

### Pré-requisitos
- Java 8 ou superior instalado
- VS Code ou qualquer IDE Java (recomendado)
- Arquivo `Books.csv` no diretório `Dataset/`

### Passo 1: Compilar o Projeto

```bash
# Compilar todos os arquivos
javac TP01/*.java
```

### Passo 2: Gerar o Arquivo Binário

```bash
# Executar o conversor CSV para binário
java TP01.GeradorBin
```

### Passo 3: Manipular o Banco de Dados

```bash
# Executar o gerenciador do arquivo binário
java TP01.ManipuladorBin
```


## Estrutura da Classe Livro

```java
public class Livro {
    protected int id;
    protected String titulo;
    protected String[] autores;
    protected int paginas;
    protected String[] generos;
    protected String descricao;
    protected long dataPublicacao; // Em milissegundos desde 1970
    protected String editora;
    protected String lingua;
    protected float mediaReviews;
    protected int qtdReviews;
    protected String thumbnail;
}
```

## Autores

**Pedro Augusto Gonçalves Gomes Amaral**
**Daniella Emily Cornelio da Silva**