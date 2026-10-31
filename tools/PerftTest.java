package dev.hjih.trapbot;
public class PerftTest {
  public static void main(String[] a){
    Object[][] T={
      {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",5,4865609L},
      {"r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",4,4085603L},
      {"8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",5,674624L},
      {"r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",4,422333L},
      {"rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",4,2103487L},
    };
    for(Object[] t:T){ Pos p=new Pos(new TT(10)); p.setFen((String)t[0]); long t0=System.nanoTime(); long n=p.perft((Integer)t[1]); long ms=(System.nanoTime()-t0)/1000000;
      System.out.println((n==(Long)t[2]?"OK  ":"FAIL")+" depth "+t[1]+" got "+n+" exp "+t[2]+" "+ms+"ms  "+(n/Math.max(1,ms))+" kn/s"); }
  }
}
