package dev.hjih.trapbot;
public class Smoke {
  static String san(Pos p,TrapEngine.Result r){ return ""+(char)('a'+r.ff)+(r.fr+1)+(char)('a'+r.tf)+(r.tr+1); }
  public static void main(String[] a){
    int th=Integer.parseInt(a.length>0?a[0]:"1");
    TrapEngine e=new TrapEngine(th);
    String[][] T={
      {"r3k3/8/8/3N4/8/8/8/4K3 w - - 0 1","fork: expect d5c7"},
      {"6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1","back-rank: expect d1d8"},
      {"r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 1","scholar mate: expect f3f7"},
      {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","startpos"},
    };
    for(String[] t:T){ long t0=System.nanoTime();
      TrapEngine.Result r=e.handle(t[0],12,2000,150,true,null);
      System.out.println(t[1]+" -> "+(r.error!=null?"ERR "+r.error:r.none?"none "+r.over:san(null,r))+"  ("+(System.nanoTime()-t0)/1000000+"ms)\n   "+r.info);
    }
    e.shutdown();
  }
}
