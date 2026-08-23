package Jogador_DataStream;

import java.text.DecimalFormat;

public class Jogador{
    
    protected int idJogador;
    protected String nome;
    protected float pontos;

    //Construtor
    public Jogador(int i, String n, float p){
        idJogador =  i;
        nome = n;
        pontos = p;  
    }
    
    //Construtor
    public Jogador(){
        idJogador =  -1;
        nome = "";
        pontos = 0F;  
    }

    //Devolve uma string com os dados do jogador
    public String toString(){
        DecimalFormat df= new DecimalFormat("#,##0.00");//formata o valor dos pontos
        return "\nID:"+idJogador +
                "\nNomes:"+nome +
                "\nPontos:"+ df.format(pontos);
    }
}