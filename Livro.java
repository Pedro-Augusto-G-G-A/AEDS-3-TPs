package TP01;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringJoiner;

public class Livro{
    
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

    //Construtor (data long)
    public Livro(int i, String t, String[] a, int p,
                    String[] g, String d, long dp, String e,
                    String l, float mr, int qr, String tn){
        setId(i);
        setTitulo(t);
        setAutores(a);
        setPaginas(p);
        setGeneros(g);
        setDescricao(d);
        setDataPublicacao(dp);
        setEditora(e);
        setLingua(l);
        setMediaReviews(mr);
        setQtdReviews(qr);
        setThumbnail(tn);
    }

    //Contrutor (Data String)
    public Livro(int i, String t, String[] a, int p,
                    String[] g, String d, String dp, String e,
                    String l, float mr, int qr, String tn) throws Exception{
        setId(i);
        setTitulo(t);
        setAutores(a);
        setPaginas(p);
        setGeneros(g);
        setDescricao(d);
        setDataPublicacao(StringToDataPublicacao(dp));
        setEditora(e);
        setLingua(l);
        setMediaReviews(mr);
        setQtdReviews(qr);
        setThumbnail(tn);
    }
    
    //Construtor vazio
    public Livro(){
        setId(-1);
        setTitulo("");
        setAutores(new String[0]);
        setPaginas(0);
        setGeneros(new String[0]);
        setDescricao("");
        setDataPublicacao(0);
        setEditora("");
        setLingua("");
        setMediaReviews(-1);
        setQtdReviews(0);
        setThumbnail("");
    }

    public int getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String[] getAutores() {
        return autores;
    }

    public int getPaginas() {
        return paginas;
    }

    public String[] getGeneros() {
        return generos;
    }

    public String getDescricao() {
        return descricao;
    }

    public long getDataPublicacao() {
        return dataPublicacao;
    }

    public String DataPublicacaoToString(long dp) {
        Date date = new Date(dataPublicacao);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        return sdf.format(date);
    }

    public String getEditora() {
        return editora;
    }

    public String getLingua() {
        return lingua;
    }

    public float getMediaReviews() {
        return mediaReviews;
    }

    public int getQtdReviews() {
        return qtdReviews;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setId(int i) {
        id = i;
    }

    public void setTitulo(String t) {
        titulo = t;
    }

    public void setAutores(String[] a) {
        autores = a;
    }

    public void setPaginas(int p) {
        paginas = p;
    }

    public void setGeneros(String[] g) {
        generos = g;
    }

    public void setDescricao(String d) {
        descricao = d;
    }

    public void setDataPublicacao(long dp) {
        dataPublicacao = dp;
    }

    public long StringToDataPublicacao(String dp) throws ParseException {
        if (dp == null || dp.trim().isEmpty()) {
            throw new IllegalArgumentException("Data não pode ser nula ou vazia");
        }
        
        dp = dp.trim();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        sdf.setLenient(false);
        
        // Tenta diferentes formatos
        if (dp.matches("\\d{4}-\\d{2}-\\d{2}")) {
            // Formato yyyy-MM-dd
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            Date date = format.parse(dp);
            return date.getTime();

        } else if (dp.matches("\\d{4}-\\d{2}")) {
            // Apenas ano e mês -> yyyy-MM
            String dataNormalizada = dp.substring(0,4) + "/" + dp.substring(5,7) + "/01";
            Date date = sdf.parse(dataNormalizada);
            return date.getTime();
            
        } else if (dp.matches("\\d{4}")) {
            // Apenas ano -> yyyy
            String dataNormalizada = dp + "/01/01";
            Date date = sdf.parse(dataNormalizada);
            return date.getTime();
        
        } else if (dp.equals("Unknown")) {
            // Data "Unknown"
            return 0;
        
        } else if (dp.matches("\\d{4}/\\d{2}/\\d{2}")) {
            // Formato yyyy/MM/dd
            Date date = sdf.parse(dp);
            return date.getTime();
        }

        else {
            throw new ParseException("Formato inválido. Use yyyy-MM-dd, yyyy/MM/dd, yyyy/MM, yyyy ou Unknown. String: " + dp, 0);
        }
    }

    public void setEditora(String e) {
        editora = e;
    }

    public void setLingua(String l) {
        lingua = l;
    }

    public void setMediaReviews(float mr) {
        mediaReviews = mr;
    }

    public void setQtdReviews(int qr) {
        qtdReviews = qr;
    }

    public void setThumbnail(String tn) {
        thumbnail = tn;
    }

    //Devolve uma string com os dados do livro
    public String toString(){
        DecimalFormat df= new DecimalFormat("#,#0.0");//formata o valor dos autores
        StringJoiner authors = new StringJoiner(", ");
        for (String autor : getAutores()) {
            authors.add(autor);
        }
        StringJoiner genres = new StringJoiner(", ");
        for (String genero : getGeneros()) {
            genres.add(genero);
        }
        return "\nID: " + getId() +
                "\ntitulos: " + getTitulo() +
                "\nautores: " + authors.toString() +
                "\npaginas: " + getPaginas() + 
                "\ngeneros: " + genres.toString() +
                "\ndescricao: " + getDescricao() + 
                "\ndata de publicacao: " + DataPublicacaoToString(getDataPublicacao()) +
                "\neditora: " + getEditora() + 
                "\nlingua: " + getLingua() + 
                "\nmedia das reviews: " + df.format(getMediaReviews()) + 
                "\nnumero de reviews: " + getQtdReviews() + 
                "\nthumbnail: " + getThumbnail();
    }
}