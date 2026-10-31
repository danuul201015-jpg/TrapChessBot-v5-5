package dev.hjih.trapbot;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Match {
  static String fen(Pos p){
    StringBuilder sb=new StringBuilder();
    for(int r=7;r>=0;r--){ int e=0; for(int f=0;f<8;f++){ int c=p.b[r*16+f]; if(c==0){e++;continue;} if(e>0){sb.append(e);e=0;} sb.append("PNBRQKpnbrqk".charAt(c<9?c-1:c-9+6)); } if(e>0)sb.append(e); if(r>0)sb.append('/'); }
    String cs=((p.cast&1)!=0?"K":"")+((p.cast&2)!=0?"Q":"")+((p.cast&4)!=0?"k":"")+((p.cast&8)!=0?"q":"");
    return sb+" "+(p.side==0?"w":"b")+" "+(cs.isEmpty()?"-":cs)+" - 0 1";
  }
  static int find(Pos p,int ff,int fr,int tf,int tr,int pr){
    for(int m:p.legalMoves()){ int f=m&127,t=(m>>7)&127; if((f&7)==ff&&(f>>4)==fr&&(t&7)==tf&&(t>>4)==tr&&((m>>14)&7)==pr) return m; }
    return -1;
  }
  static int num(String s,String k){ Matcher m=Pattern.compile("\""+k+"\":(-?\\d+)").matcher(s); return m.find()?Integer.parseInt(m.group(1)):-1; }
  public static void main(String[] a) throws Exception {
    int games=Integer.parseInt(a[0]); int g0=a.length>4?Integer.parseInt(a[4]):0; long ms=Long.parseLong(a[1]); int cun=Integer.parseInt(a[2]); int threads=Integer.parseInt(a[3]);
    Process pr=new ProcessBuilder("node","old_run.js").redirectErrorStream(false).start();
    BufferedWriter w=new BufferedWriter(new OutputStreamWriter(pr.getOutputStream())); BufferedReader rd=new BufferedReader(new InputStreamReader(pr.getInputStream()));
    int win=0,loss=0,draw=0; long jt=0,jn=0,nt=0,nn=0;
    for(int g=g0;g<g0+games;g++){
      TrapEngine ne=new TrapEngine(threads);
      // сброс старого движка: стартовая позиция сбрасывает pending сама, т.к. fen не продолжает партию
      Pos ref=new Pos(new TT(8)); ref.setFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
      Random rnd=new Random(1000+g/2);
      for(int i=0;i<6;i++){ int[] l=ref.legalMoves(); ref.make(l[rnd.nextInt(l.length)]); }
      boolean newWhite=(g%2==0); String res="*"; int ply=0;
      while(true){
        String ov=ref.over();
        if(!ov.isEmpty()){ if(ov.startsWith("Мат")){ boolean whiteWon=ov.contains("белые"); res=(whiteWon==newWhite)?"new":"old"; } else res="draw"; break; }
        if(ply>=240){res="draw";break;}
        boolean newMoves=(ref.side==0)==newWhite;
        int m;
        long t0=System.nanoTime();
        if(newMoves){
          TrapEngine.Result r=ne.handle(fen(ref),100,ms,cun,true,null);
          if(r.error!=null||r.none){System.out.println("new engine err: "+r.error+" "+r.over);res="old";break;}
          m=find(ref,r.ff,r.fr,r.tf,r.tr,r.pr);
          nt+=System.nanoTime()-t0; nn++;
        } else {
          w.write("{\"id\":1,\"fen\":\""+fen(ref)+"\",\"depth\":100,\"ms\":"+ms+",\"cun\":"+cun+",\"adapt\":true}\n"); w.flush();
          String line=rd.readLine(); if(line==null)throw new IOException("node died");
          if(line.contains("\"error\"")||line.contains("\"none\"")){System.out.println("old engine err: "+line);res="new";break;}
          m=find(ref,num(line,"ff"),num(line,"fr"),num(line,"tf"),num(line,"tr"),Math.max(0,num(line,"pr")));
          jt+=System.nanoTime()-t0; jn++;
        }
        if(m<0){System.out.println((newMoves?"NEW":"OLD")+" illegal move!");res=newMoves?"old":"new";break;}
        ref.make(m); ply++;
      }
      ne.shutdown();
      if(res.equals("new"))win++; else if(res.equals("old"))loss++; else draw++;
      System.out.println("game "+g+" new="+(newWhite?"white":"black")+" result="+res+" plies="+ply+"   score new "+win+"-"+draw+"-"+loss);
    }
    System.out.printf("TOTAL new(Java) +%d =%d -%d  of %d  (score %.1f%%)%n",win,draw,loss,games,100.0*(win+draw/2.0)/games);
    pr.destroy();
  }
}
