# AEDS-3-TPs - Banco de Dados Binário de Livros

## Visão Geral do Projeto

Este projeto implementa um sistema de banco de dados de livros em arquivo binário usando Java, com **índice em Árvore B** armazenado em disco. É composto por cinco componentes principais que trabalham juntos para fornecer armazenamento e manipulação eficiente de dados:

1. **Conversor CSV para Binário** (`GeradorBin.java`) — Lê dados de livros de um arquivo CSV, converte para um formato binário personalizado e constrói o índice em Árvore B.
2. **Árvore B em Disco** (`ArvoreBMais.java`) — Implementa uma Árvore B parametrizável em ordem, mapeando `id → posição no arquivo binário`, com operações de inserção, busca, atualização de endereço e remoção com redistribuição/merge.
3. **Gerenciador de Arquivo Binário** (`Menu.java`) — Fornece um menu interativo para o usuário escolher como alterar os dados.
4. **Manipulador de Arquivo Binário** (`CRUD.java`) — Fornece operações CRUD (Create, Read, Update, Delete) usando o índice da Árvore B para localização direta de registros.
5. **Ordenador de Arquivo Binário** (`Ordenacao.java`) — Ordena o arquivo binário, deletando fisicamente registros logicamente deletados, com atualização em massa das posições na Árvore B.

---

## Estrutura do Projeto

```
TP02/
├── GeradorBin.java          # Conversor CSV para Binário + construção do índice
├── Menu.java                # Gerenciador do arquivo binário com CRUD e ordenação
├── CRUD.java                # Arquivo responsável pelo CRUD (usa a Árvore B)
├── Ordenacao.java           # Arquivo responsável pela ordenação
├── ArvoreBMais.java         # Índice em Árvore B (arquivo em disco)
├── Livro.java               # Classe entidade Livro
└── Dataset/
    └── Books.csv            # Conjunto de dados fonte (informações dos livros)
```

Os arquivos gerados em tempo de execução são:

```
TP02/
├── livros.bin               # Arquivo de dados (registros dos livros)
└── arvore.bin               # Arquivo de índice (Árvore B: id → posição)
```

---

## Formato do Arquivo Binário

O arquivo binário (`livros.bin`) segue um formato personalizado otimizado para acesso eficiente aos dados:

```
[INT: total_registros]
[
  [INT: id]                                           // ID do livro (4 bytes)
  [INT: tam_registro]                                 // Tamanho do registro a seguir
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

> **Observação:** o primeiro campo do arquivo (`total_registros`) funciona, na prática, como "último id usado". O `GeradorBin` grava a contagem total (ids contíguos de 1 a N), e o `CRUD.create` sobrescreve com o id mais recente. Isso é consistente porque o gerador produz ids contínuos.

---

## Índice em Árvore B

### Por que uma Árvore B?

Sem índice, qualquer operação de leitura/atualização/remoção exige **varredura sequencial** do arquivo inteiro (O(n) acessos a disco). Com uma Árvore B em disco mapeando `id → posição`, cada operação cai para **O(log_d n)** acessos — e o índice nunca precisa caber inteiro em RAM.

### Estrutura do arquivo de índice (`arvore.bin`)

```
[INT: ordem d]
[LONG: posição da raiz (-1 se vazia)]
[
  [INT: n]  // número de chaves no nó
  d × (
        [LONG: pos_filho_esq]
        [INT:  id]
        [LONG: pos_registro_no_arquivo_bin]
        [LONG: pos_filho_dir]
      )
] × M
```

- A **ordem `d`** é parametrizada em tempo de execução (o `GeradorBin` pergunta ao usuário, mínimo 3).
- Cada nó tem **no máximo `d` chaves** e **`d + 1` filhos**.
- Cada nó (exceto a raiz) tem **no mínimo `(d - 1) / 2` chaves**.
- Todos os nós são de **tamanho fixo** (`4 + d × 28` bytes), o que permite `seek` direto a qualquer nó por `posição = header + k × TAMANHO_NO`.

### Convenção dos filhos

Cada chave do nó carrega explicitamente dois filhos (`esq` e `dir`), conforme o layout pedido. Como consequência, o filho mais à esquerda (antes da chave 0) e o mais à direita (depois da última chave) precisam de uma convenção:

- `filho_esq[0]` = filho mais à esquerda do nó.
- `filho_dir[n-1]` = filho mais à direita do nó.
- Os filhos intermediários são redundantes: `filho_dir[i] == filho_esq[i+1]`, mantendo o invariante `n + 1` filhos.

### Operações implementadas

| Operação | Método | Complexidade |
|---|---|---|
| Inserir `(id, pos)` | `insere(int, long)` | O(log_d n) |
| Buscar `pos` pelo `id` | `busca(int)` | O(log_d n) |
| Atualizar posição de um `id` | `atualiza(int, long)` | O(log_d n) |
| Remover `id` (com merge/redistribuição) | `remove(int)` | O(log_d n) |

A **inserção** usa o método clássico de *split preventivo* (divide o filho cheio antes de descer). A **remoção** usa *fill preventivo* (garante que o filho tenha mais que o mínimo de chaves antes de descer), evitando underflow. Quando um nó interno precisa perder uma chave, ela é substituída pelo **predecessor** ou **sucessor** antes da remoção descer. Se a raiz ficar com 0 chaves, a árvore encolhe.

### Consistência com o arquivo de dados

A posição guardada na Árvore B é **o byte onde começa o campo `id`** do registro (não a lápide, não o tamanho). Assim, para chegar à lápide, basta `seek(pos + 8)`. Esse formato é consistente em todos os pontos:

- `GeradorBin` grava `posicao = getFilePointer()` antes de escrever o `id`.
- `CRUD.create` faz o mesmo.
- `CRUD.update` (quando move o registro pro fim) chama `arvore.atualiza(id, novaPos)` com a mesma convenção.
- `CRUD.delete` chama `arvore.remove(id)`.

---

## Funcionalidades

### 1. Conversor CSV para Binário (`GeradorBin.java`)

- Solicita ao usuário a **ordem da Árvore B** (inteiro ≥ 3), validando a entrada.
- Lê os dados do arquivo `Books.csv`.
    - `Books.csv` vêm originalmente de https://www.kaggle.com/datasets/madankhatri123h/books-dataset
        - Modificações à base original:
            - O livro "Agatha Christie: The Queen of Murder Mysteries (Biography of Her Life)" estava com uma data errada de "101-01-01". Isso nos levou a perceber que o livro é fictício, não existe, então essa remoção foi a única remoção feita no CSV original.
            - O livro "Tales from Shakespeare" estava com uma data errada de "19??". Corrigimos a data com base na pesquisa de sua data de lançamento oficial: 2011/07/30.
            - O livro "L'amore bugiardo" possuía sua data em formato datetime, diferentemente do resto inteiro da base de dados. 2012-12-13T00:00:00+01:00 foi alterado para somente 2012-12-13.
        - Detalhes de implementação:
            - Livros com "Unknown" no campo "pages" tiveram seu número de páginas colocadas como -1.
            - Livros com "No rating" no campo "average_rating" tiveram sua média de reviews preenchida como -1.
            - Livros com datas incompletas (somente o ano ou somente ano-mês) foram completados com 01 nos campos restantes.
            - Livros com "Unknown" no campo de datas foram preenchidos como 1º de janeiro de 1970 (long = 0).
- Ignora a linha de cabeçalho do CSV.
- Faz o parsing do CSV com tratamento adequado de campos entre aspas (incluindo vírgulas dentro das aspas).
- Usa `RandomAccessFile` para gravar cada registro e **capturar a posição** antes da escrita.
- Para cada livro gravado, insere a chave `(id, posição)` na Árvore B.
- Ao final, atualiza o cabeçalho com o último id usado.

### 2. Árvore B em Disco (`ArvoreBMais.java`)

- **Ordem parametrizável** (mínimo 3), persistida no cabeçalho do arquivo.
- Dois construtores:
    - `ArvoreBMais(File, int ordem)` — cria/recria o arquivo com a ordem fornecida.
    - `ArvoreBMais(File)` — abre um arquivo existente e lê a ordem do cabeçalho.
- Rejeita abrir um arquivo cuja ordem não corresponde à ordem solicitada.
- Todas as operações fazem `seek` direto aos nós — nada é mantido em RAM além de um nó por vez.

### 3. Gerenciador de Arquivo Binário (`Menu.java`)

- Menu principal com opções **CRUD**, **Ordenação** e **Sair**.
- Validação de todas as entradas numéricas (opções do menu, ids de livros, etc.), repetindo a leitura até obter um valor válido.
- Abre simultaneamente o **arquivo de dados** (`RandomAccessFile`) e a **Árvore B**, passando-os para as operações do CRUD.
- **Manipulador de Arquivo Binário (`CRUD.java`)**:
    - **Create (Criar)**: Adiciona novos livros ao banco, gravando no fim do arquivo, atualizando o cabeçalho e **inserindo a nova chave na Árvore B**.
    - **Read (Ler)**: Recupera informações de um livro pelo ID, **consultando a Árvore B** para localizar a posição e lendo apenas o registro-alvo.
    - **Update (Atualizar)**: Modifica registros existentes. Se o novo conteúdo couber no espaço antigo, sobrescreve no lugar; caso contrário, marca a lápide no registro antigo, grava o novo no fim e **atualiza a posição na Árvore B**.
    - **Delete (Deletar)**: Exclusão lógica com lápide **e remoção da chave na Árvore B** (com redistribuição/merge).
- **Ordenador de Arquivo Binário (`Ordenacao.java`)**:
    - Ordena os registros por diferentes critérios (ID, título, páginas, etc.).
    - Após a reordenação, a **Árvore B é atualizada em massa**: o `Menu` varre sequencialmente o arquivo já reordenado e chama `arvore.atualiza(id, novaPos)` para cada registro, mantendo o índice consistente sem carregar nada em memória além de um registro por vez.

---

## Fluxo de Uso do Índice

```
GeradorBin
   │
   │ 1. pergunta ordem d
   │ 2. lê CSV linha a linha
   │ 3. escreve registro em livros.bin e captura posição
   │ 4. arvore.insere(id, posição)
   ▼
livros.bin + arvore.bin prontos

Menu
   │
   ├── CRUD
   │     ├── create → grava no fim e arvore.insere
   │     ├── read   → arvore.busca(id) → seek(pos + 8) → lerRegistro
   │     ├── update → arvore.busca(id)
   │     │              ├── cabe no lugar    → sobrescreve
   │     │              └── não cabe         → lápide + grava no fim
   │     │                                     + arvore.atualiza(id, novaPos)
   │     └── delete → arvore.busca(id) → marca lápide + arvore.remove(id)
   │
   └── Ordenar
         ├── fecha raf e arvore
         ├── Ordenacao.main(...) reordena livros.bin
         ├── reabre raf e arvore
         └── varre livros.bin e chama arvore.atualiza(id, pos) por registro
```

---

## Tecnologias Utilizadas

- **Java 8+** — Linguagem de programação
- **java.io** — `RandomAccessFile`, `BufferedReader`, `FileReader`, `File`
- **java.util** — `ArrayList`, `List`, `Scanner`
- **java.nio.file** — `Path`, `Paths`

---

## Como Usar

### Pré-requisitos

- Java 8 ou superior instalado
- VS Code ou qualquer IDE Java (recomendado)
- Arquivo `Books.csv` no diretório `TP02/Dataset/`

### Passo 1: Compilar o Projeto

```bash
javac TP02/*.java
```

### Passo 2: Gerar os Arquivos Binário e de Índice

```bash
java TP02.GeradorBin
```

Será solicitada a **ordem da Árvore B** (inteiro ≥ 3). Ao final, são gerados:

- `TP02/livros.bin` — registros dos livros
- `TP02/arvore.bin` — índice `id → posição`

### Passo 3: Manipular o Banco de Dados

```bash
java TP02.Menu
```

O menu oferece:

```
===== SISTEMA GERENCIADOR DE BANCO DE DADOS - LIVROS =====
1 - CRUD
2 - Ordenar
0 - Sair
```

No CRUD:

```
----- CRUD -----
1 - Inserir um novo livro
2 - Buscar livro (pelo ID)
3 - Atualizar livro
4 - Deletar livro
0 - Voltar
```

---

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

---

## Detalhes de Implementação da Árvore B

### Split preventivo na inserção

Antes de descer para um filho, se ele estiver **cheio** (`n == d`), ele é dividido: a chave mediana sobe pro pai, e as duas metades viram nós separados. Se a raiz estiver cheia, ela é dividida primeiro e uma nova raiz é criada — é assim que a árvore cresce em altura.

### Fill preventivo na remoção

Antes de descer para um filho, se ele estiver com **exatamente o mínimo** de chaves, ele é "reforçado":

1. Se um irmão adjacente tiver mais que o mínimo, **empresta** uma chave (via rotação pelo pai).
2. Caso contrário, faz **merge** com o irmão + a chave do meio do pai.

Isso garante que, quando a remoção de fato acontecer na folha, ela ainda vai ter ≥ mínimo de chaves, e nenhum underflow precisa ser corrigido na volta.

### Substituição por predecessor/sucessor

Quando a chave a remover está em um **nó interno**, ela é substituída pelo **maior id da subárvore esquerda** (predecessor) ou pelo **menor id da subárvore direita** (sucessor), e a remoção continua descendo até a folha. A escolha depende de qual dos dois filhos tem mais que o mínimo de chaves — se ambos tiverem, usa o predecessor.

### Encolhimento da árvore

Se após uma remoção a raiz fica com 0 chaves e 1 filho, esse filho vira a nova raiz. Se a raiz fica com 0 chaves e é folha, a árvore fica vazia (`raiz = -1`).

### Atualização em massa após ordenação

Como a ordenação reorganiza **todos** os registros do arquivo, todas as posições do índice ficam obsoletas. A solução implementada:

1. Após `Ordenacao.main(...)`, o `Menu` varre o arquivo sequencialmente (`[id][tam][lápide][dados]`).
2. Para cada registro, chama `arvore.atualiza(id, novaPos)`.
3. Custo de memória: **O(1)** — apenas o registro corrente.
4. Custo de tempo: O(n · log_d n), mas sem carregar nada proporcional ao dataset em RAM.

---

## Autores

- **Pedro Augusto Gonçalves Gomes Amaral**
- **Daniella Emily Cornelio da Silva**