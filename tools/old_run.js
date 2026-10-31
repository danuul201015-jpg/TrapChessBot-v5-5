const E=(function(){
const b=new Int8Array(128),KN=[-33,-31,-18,-14,14,18,31,33],KG=[-17,-16,-15,-1,1,15,16,17],BD=[-17,-15,15,17],RD=[-16,-1,1,16];
const VAL=[0,100,320,330,500,900,20000],INF=1e9,MATE=1e5,TO={};
let side=0,cast=15,ep=-1,hm=0,sp=0,hh=0,hl=0,nodes=0,deadline=0;
const ksq=[4,116],npc=[0,0],SZ=1200;
const S_m=new Int32Array(SZ),S_cp=new Int8Array(SZ),S_c=new Int8Array(SZ),S_ep=new Int16Array(SZ),S_hm=new Int16Array(SZ),S_hh=new Int32Array(SZ),S_hl=new Int32Array(SZ);
let seed=123456789;const rnd=()=>{seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;return seed|0};
const Zh=new Int32Array(2048),Zl=new Int32Array(2048),ZCh=new Int32Array(16),ZCl=new Int32Array(16),ZEh=new Int32Array(8),ZEl=new Int32Array(8);
for(let i=0;i<2048;i++){Zh[i]=rnd();Zl[i]=rnd()}
for(let i=0;i<16;i++){ZCh[i]=rnd();ZCl[i]=rnd()}for(let i=0;i<8;i++){ZEh[i]=rnd();ZEl[i]=rnd()}
const ZSh=rnd(),ZSl=rnd();
const CR=new Int8Array(128).fill(15);CR[0]=13;CR[4]=12;CR[7]=14;CR[112]=7;CR[116]=3;CR[119]=11;
const CEN=new Int8Array(128);for(let s=0;s<128;s++)if(!(s&0x88)){const f=s&7,r=s>>4;CEN[s]=Math.min(f,7-f)+Math.min(r,7-r)}
const MV=[],SC=[];for(let i=0;i<130;i++){MV.push(new Int32Array(256));SC.push(new Int32Array(256))}
const K1=new Int32Array(130),K2=new Int32Array(130),HIST=new Int32Array(2048);
const TS=1<<19,TM=TS-1,tk1=new Int32Array(TS),tk2=new Int32Array(TS),tv=new Int32Array(TS),td=new Int8Array(TS),tf=new Int8Array(TS),tmv=new Int32Array(TS);
function hashAll(){hh=0;hl=0;for(let s=0;s<128;s++){if(s&0x88){s+=7;continue}if(b[s]){hh^=Zh[b[s]*128+s];hl^=Zl[b[s]*128+s]}}
 if(side){hh^=ZSh;hl^=ZSl}hh^=ZCh[cast];hl^=ZCl[cast];if(ep>=0){hh^=ZEh[ep&7];hl^=ZEl[ep&7]}}
function setFen(fen){const p=fen.split(' ');b.fill(0);npc[0]=npc[1]=0;let r=7,f=0;
 for(const ch of p[0]){if(ch==='/'){r--;f=0}else if(ch>='1'&&ch<='8')f+=+ch;else{const i='PNBRQKpnbrqk'.indexOf(ch),pc=i<6?i+1:i-6+9,s=r*16+f;b[s]=pc;
  if((pc&7)===6)ksq[pc>>3]=s;else if((pc&7)>1)npc[pc>>3]++;f++}}
 side=p[1]==='b'?1:0;cast=0;for(const ch of p[2]||'-')cast|={K:1,Q:2,k:4,q:8}[ch]||0;
 ep=p[3]&&p[3]!=='-'?(+p[3][1]-1)*16+'abcdefgh'.indexOf(p[3][0]):-1;hm=+p[4]||0;sp=0;hashAll();TTclear()}
function TTclear(){tk1.fill(0);tk2.fill(0);td.fill(-1);HIST.fill(0)}
function att(sq,by,all){let best=1e9;const c8=by<<3;
 for(let k=0;k<2;k++){const s=sq+(by?(k?17:15):(k?-17:-15));if(!(s&0x88)&&b[s]===(1|c8)){if(!all)return 100;best=100}}
 for(let k=0;k<8;k++){const s=sq+KN[k];if(!(s&0x88)&&b[s]===(2|c8)){if(!all)return 320;if(best>320)best=320}}
 for(let k=0;k<8;k++){const s=sq+KG[k];if(!(s&0x88)&&b[s]===(6|c8)){if(!all)return 20000;if(best>20000)best=20000}}
 for(let k=0;k<4;k++){const d=BD[k];let s=sq+d;while(!(s&0x88)){const p=b[s];if(p){if(p===(3|c8)||p===(5|c8)){const v=VAL[p&7];if(!all)return v;if(v<best)best=v}break}s+=d}}
 for(let k=0;k<4;k++){const d=RD[k];let s=sq+d;while(!(s&0x88)){const p=b[s];if(p){if(p===(4|c8)||p===(5|c8)){const v=VAL[p&7];if(!all)return v;if(v<best)best=v}break}s+=d}}
 return best}
const add=(L,n,f,t,fl,pr)=>{L[n]=f|(t<<7)|(pr<<14)|(fl<<17);return n+1};
function gen(L,caps){let n=0;const us=side<<3;
 for(let s=0;s<128;s++){if(s&0x88){s+=7;continue}
  const p=b[s];if(!p||(p&8)!==us)continue;const pt=p&7;
  if(pt===1){const d=side?-16:16,r=s>>4,pr=r===(side?1:6),t1=s+d;
   if(!b[t1]){if(pr){n=add(L,n,s,t1,0,5);if(!caps){n=add(L,n,s,t1,0,2);n=add(L,n,s,t1,0,4);n=add(L,n,s,t1,0,3)}}
    else if(!caps){n=add(L,n,s,t1,0,0);if(r===(side?6:1)&&!b[t1+d])n=add(L,n,s,t1+d,4,0)}}
   for(let k=-1;k<=1;k+=2){const t=s+d+k;if(t&0x88)continue;const c=b[t];
    if(c&&(c&8)!==us){if(pr){n=add(L,n,s,t,0,5);if(!caps){n=add(L,n,s,t,0,2);n=add(L,n,s,t,0,4);n=add(L,n,s,t,0,3)}}else n=add(L,n,s,t,0,0)}
    else if(!c&&t===ep)n=add(L,n,s,t,1,0)}
  }else if(pt===2||pt===6){const D=pt===2?KN:KG;
   for(let k=0;k<8;k++){const t=s+D[k];if(t&0x88)continue;const c=b[t];if(!c){if(!caps)n=add(L,n,s,t,0,0)}else if((c&8)!==us)n=add(L,n,s,t,0,0)}
   if(pt===6&&!caps&&s===(side?116:4)){const th=side^1,kb=side?4:1,qb=side?8:2;
    if((cast&kb)&&!b[s+1]&&!b[s+2]&&att(s,th)>=1e9&&att(s+1,th)>=1e9&&att(s+2,th)>=1e9)n=add(L,n,s,s+2,2,0);
    if((cast&qb)&&!b[s-1]&&!b[s-2]&&!b[s-3]&&att(s,th)>=1e9&&att(s-1,th)>=1e9&&att(s-2,th)>=1e9)n=add(L,n,s,s-2,2,0)}
  }else{for(let g=0;g<2;g++){if(g===0&&pt===4)continue;if(g===1&&pt===3)continue;const D=g?RD:BD;
    for(let k=0;k<4;k++){const d=D[k];let t=s+d;while(!(t&0x88)){const c=b[t];if(!c){if(!caps)n=add(L,n,s,t,0,0)}else{if((c&8)!==us)n=add(L,n,s,t,0,0);break}t+=d}}}}
 }
 return n}
function make(m){const f=m&127,t=(m>>7)&127,pr=(m>>14)&7,fl=m>>17,p=b[f],cp=b[t];
 S_m[sp]=m;S_cp[sp]=cp;S_c[sp]=cast;S_ep[sp]=ep;S_hm[sp]=hm;S_hh[sp]=hh;S_hl[sp]=hl;
 if(ep>=0){hh^=ZEh[ep&7];hl^=ZEl[ep&7]}hh^=ZCh[cast];hl^=ZCl[cast];
 hh^=Zh[p*128+f];hl^=Zl[p*128+f];hm++;if((p&7)===1||cp)hm=0;b[f]=0;
 if(cp){hh^=Zh[cp*128+t];hl^=Zl[cp*128+t];const c=cp&7;if(c>1&&c<6)npc[cp>>3]--}
 if(fl&1){const cs=t+(side?16:-16),c2=b[cs];hh^=Zh[c2*128+cs];hl^=Zl[c2*128+cs];b[cs]=0}
 let np=p;if(pr){np=pr|(side<<3);npc[side]++}
 b[t]=np;hh^=Zh[np*128+t];hl^=Zl[np*128+t];
 if(fl&2){const rf=t>f?f+3:f-4,rt=t>f?f+1:f-1,r=b[rf];b[rf]=0;b[rt]=r;hh^=Zh[r*128+rf]^Zh[r*128+rt];hl^=Zl[r*128+rf]^Zl[r*128+rt]}
 if((p&7)===6)ksq[side]=t;
 cast&=CR[f]&CR[t];ep=(fl&4)?(f+t)>>1:-1;
 if(ep>=0){hh^=ZEh[ep&7];hl^=ZEl[ep&7]}hh^=ZCh[cast];hl^=ZCl[cast];
 side^=1;hh^=ZSh;hl^=ZSl;sp++}
function unmake(){sp--;const m=S_m[sp];side^=1;
 if(m===0){}else{const f=m&127,t=(m>>7)&127,pr=(m>>14)&7,fl=m>>17;let p=b[t];
  if(pr){p=1|(side<<3);npc[side]--}
  b[f]=p;b[t]=S_cp[sp];const cp=S_cp[sp],c=cp&7;if(cp&&c>1&&c<6)npc[cp>>3]++;
  if(fl&1){b[t]=0;b[t+(side?16:-16)]=1|((side^1)<<3)}
  if(fl&2){const rf=t>f?f+3:f-4,rt=t>f?f+1:f-1;b[rf]=b[rt];b[rt]=0}
  if((p&7)===6)ksq[side]=f}
 cast=S_c[sp];ep=S_ep[sp];hm=S_hm[sp];hh=S_hh[sp];hl=S_hl[sp]}
function makeNull(){S_m[sp]=0;S_c[sp]=cast;S_ep[sp]=ep;S_hm[sp]=hm;S_hh[sp]=hh;S_hl[sp]=hl;
 if(ep>=0){hh^=ZEh[ep&7];hl^=ZEl[ep&7]}ep=-1;side^=1;hh^=ZSh;hl^=ZSl;hm++;sp++}
const FLG={pin:0,sh:1,pp:1};
const wpf=new Int8Array(8),bpf=new Int8Array(8),rk=new Int16Array(8),pq=new Int16Array(20),wmin=new Int8Array(8),bmax=new Int8Array(8),PP=[0,0,5,10,22,38,60,0];
// связки и сквозные удары слайдера q цвета w (w=true — белые): сумма оценок против фигур соперника
function pinScan(q,w){const p=b[q],pt=p&7,me=w?0:1,op=w?1:0;let sc=0;
 for(let g=0;g<2;g++){if(g===0&&pt===4)continue;if(g===1&&pt===3)continue;const D=g?RD:BD;
  for(let k=0;k<4;k++){const d=D[k];let s=q+d,a=0,as=0;
   while(!(s&0x88)){const x=b[s];
    if(x){const en=((x&8)!==0)===w;
     if(!a){if(!en)break;a=x&7;as=s}
     else{if(en){const B=x&7;
       if(a===6){if(VAL[B]>=500)sc+=26}
       else if(B===6){sc+=16+(a>2?10:0);
        if(att(as,me,true)<VAL[a])sc+=VAL[a]/18|0;
        if(att(as,op,false)>=1e9)sc+=VAL[a]/14|0}
       else if(VAL[B]>VAL[a]+50){let v=Math.min(30,(VAL[B]-VAL[a])/14|0);
        if(att(as,me,true)<VAL[a]||att(as,op,false)>=1e9)v+=12;sc+=v}
       else if(VAL[a]>VAL[B]+100&&(VAL[pt]<VAL[a]||att(as,op,false)>=1e9))sc+=Math.min(22,(VAL[a]-VAL[B])/25|0)}
      break}}
    s+=d}}}
 return sc}
function pinsOf(w){let t=0;for(let q=0;q<128;q++){if(q&0x88){q+=7;continue}const p=b[q];if(!p)continue;const pt=p&7;
  if(pt>=3&&pt<=5&&(!(p&8))===w)t+=pinScan(q,w)}return t}
function evaluate(){let s=0,np=0,wb=0,bb=0,nr=0,npw=0;wpf.fill(0);bpf.fill(0);wmin.fill(9);bmax.fill(-1);
 for(let q=0;q<128;q++){if(q&0x88){q+=7;continue}const p=b[q];if(!p)continue;
  const pt=p&7,w=!(p&8),f=q&7,rel=w?(q>>4):7-(q>>4),c=CEN[q];let v=VAL[pt];
  if(pt===1){v+=rel*rel*2+rel*3+(c>=5&&rel>=2?8:0);if(w){wpf[f]++;if((q>>4)<wmin[f])wmin[f]=q>>4}else{bpf[f]++;if((q>>4)>bmax[f])bmax[f]=q>>4}pq[npw++]=q}
  else if(pt===2){v+=(c-3)*9;np+=320}
  else if(pt===3){v+=(c-3)*5;np+=330;if(w)wb++;else bb++;if(FLG.pin)v+=pinScan(q,w)}
  else if(pt===4){np+=500;if(rel===6)v+=14;rk[nr++]=q;if(FLG.pin)v+=pinScan(q,w)}
  else if(pt===5){np+=900;if(FLG.pin)v+=pinScan(q,w)}else v=0;
  s+=w?v:-v}
 const mg=Math.min(1,Math.max(0,(np-1500)/3000));
 for(let c=0;c<2;c++){const k=ksq[c],ce=CEN[k],home=(k>>4)===(c?7:0)&&((k&7)<3||(k&7)>5);
  const kt=mg*(-ce*7+(home?25:0))+(1-mg)*ce*9;s+=c?-kt:kt;
  if(FLG.sh&&mg>0&&(k>>4)===(c?7:0)){const kf=k&7,dr=c?-16:16,pc=1|(c<<3);let sh=0;
   for(let df=-1;df<=1;df++){const ff=kf+df;if(ff<0||ff>7)continue;const s1=k+dr+df;
    if(b[s1]===pc)sh+=9;else if(b[s1+dr]===pc)sh+=4;else sh-=9}
   s+=c?-mg*sh:mg*sh}}
 for(let i=0;FLG.pp&&i<npw;i++){const q=pq[i],w=!(b[q]&8),f=q&7,r=q>>4;let ps=true;
  for(let df=-1;df<=1&&ps;df++){const ff=f+df;if(ff<0||ff>7)continue;if(w){if(bmax[ff]>r)ps=false}else if(wmin[ff]<r)ps=false}
  if(ps){const bv=PP[w?r:7-r]*(0.6+0.4*(1-mg))|0;s+=w?bv:-bv}}
 for(let f=0;f<8;f++){
  if(wpf[f]>1)s-=12*(wpf[f]-1);if(bpf[f]>1)s+=12*(bpf[f]-1);
  if(wpf[f]&&!(f>0&&wpf[f-1])&&!(f<7&&wpf[f+1]))s-=10;
  if(bpf[f]&&!(f>0&&bpf[f-1])&&!(f<7&&bpf[f+1]))s+=10}
 for(let i=0;i<nr;i++){const q=rk[i],f=q&7,w=!(b[q]&8),own=w?wpf:bpf,opp=w?bpf:wpf;let v=0;
  if(!own[f]){v+=12;if(!opp[f])v+=10}s+=w?v:-v}
 if(wb>1)s+=30;if(bb>1)s-=30;
 s+=side?-10:10;return side?-s:s}
function qs(a,be,pl){if((++nodes&2047)===0&&Date.now()>deadline)throw TO;
 const st=evaluate();if(st>=be||pl>120)return st;if(st>a)a=st;let best=st;
 const L=MV[pl],S=SC[pl],n=gen(L,true);
 for(let i=0;i<n;i++){const m=L[i],t=(m>>7)&127;S[i]=(b[t]?(b[t]&7)*100:100)*10-(b[m&127]&7)+(((m>>14)&7)?5000:0)}
 for(let i=0;i<n;i++){let j=i;for(let k=i+1;k<n;k++)if(S[k]>S[j])j=k;
  if(j!==i){let x=L[i];L[i]=L[j];L[j]=x;x=S[i];S[i]=S[j];S[j]=x}
  const m=L[i],t=(m>>7)&127;if(!((m>>14)&7)&&st+VAL[b[t]&7||1]+200<a)continue;
  make(m);if(att(ksq[side^1],side,false)<1e9){unmake();continue}
  const v=-qs(-be,-a,pl+1);unmake();
  if(v>best){best=v;if(v>a){a=v;if(a>=be)break}}}
 return best}
function search(d,a,be,pl,nullOk){if((++nodes&2047)===0&&Date.now()>deadline)throw TO;
 if(pl>0){if(hm>=100)return 0;for(let i=sp-2;i>=0&&i>=sp-hm;i-=2)if(S_hh[i]===hh&&S_hl[i]===hl)return 0}
 const inChk=att(ksq[side],side^1,false)<1e9;if(inChk&&pl<60)d++;
 if(d<=0)return qs(a,be,pl);if(pl>120)return evaluate();
 const idx=hl&TM;let ttm=0;
 if(tk1[idx]===hh&&tk2[idx]===hl&&td[idx]>=0){ttm=tmv[idx];if(td[idx]>=d&&pl>0){let v=tv[idx];if(v>MATE-200)v-=pl;else if(v<-MATE+200)v+=pl;const f=tf[idx];
  if(f===0||(f===1&&v>=be)||(f===2&&v<=a))return v}}
 const pv=be-a>1;
 if(!pv&&!inChk&&d<=3&&be<MATE/2&&be>-MATE/2){const e=evaluate();if(e-130*d>=be)return e}
 if(nullOk&&!pv&&!inChk&&d>=3&&npc[side]>0){makeNull();const v=-search(d-3-(d>6?1:0),-be,-be+1,pl+1,false);unmake();if(v>=be)return be}
 const L=MV[pl],S=SC[pl],n=gen(L,false);
 for(let i=0;i<n;i++){const m=L[i],t=(m>>7)&127;let sc;
  if(m===ttm)sc=1e7;else if(b[t]||((m>>17)&1))sc=1e6+(b[t]?(b[t]&7):1)*100-(b[m&127]&7);
  else if((m>>14)&7)sc=9e5;else if(m===K1[pl])sc=8e5;else if(m===K2[pl])sc=7e5;else sc=HIST[(b[m&127]&15)*128+t];S[i]=sc}
 let best=-INF,bm=0,legal=0;const a0=a;
 for(let i=0;i<n;i++){let j=i;for(let k=i+1;k<n;k++)if(S[k]>S[j])j=k;
  if(j!==i){let x=L[i];L[i]=L[j];L[j]=x;x=S[i];S[i]=S[j];S[j]=x}
  const m=L[i],t=(m>>7)&127,quiet=!b[t]&&!((m>>17)&1)&&!((m>>14)&7),hp=(b[m&127]&15)*128+t;
  make(m);if(att(ksq[side^1],side,false)<1e9){unmake();continue}
  legal++;let v;
  if(legal===1)v=-search(d-1,-be,-a,pl+1,true);
  else{let red=0;if(quiet&&d>=3&&!inChk&&legal>3)red=legal>9?2:1;
   v=-search(d-1-red,-a-1,-a,pl+1,true);if(v>a&&red>0)v=-search(d-1,-a-1,-a,pl+1,true);
   if(v>a&&v<be)v=-search(d-1,-be,-a,pl+1,true)}
  unmake();
  if(v>best){best=v;bm=m;if(v>a){a=v;if(a>=be){if(quiet){K2[pl]=K1[pl];K1[pl]=m;HIST[hp]+=d*d}break}}}}
 if(!legal)return inChk?-MATE+pl:0;
 let sv=best;if(sv>MATE-200)sv+=pl;else if(sv<-MATE+200)sv-=pl;
 tk1[idx]=hh;tk2[idx]=hl;tv[idx]=sv;td[idx]=d;tmv[idx]=bm;tf[idx]=best<=a0?2:best>=be?1:0;
 return best}
function legalMoves(){const L=MV[128],n=gen(L,false),out=[];
 for(let i=0;i<n;i++){const m=L[i];make(m);if(att(ksq[side^1],side,false)>=1e9)out.push(m);unmake()}return out}
function hasLegal(lv){const L=MV[lv],n=gen(L,false);
 for(let i=0;i<n;i++){make(L[i]);const ok=att(ksq[side^1],side,false)>=1e9;unmake();if(ok)return true}return false}
const isMate=lv=>att(ksq[side],side^1,false)<1e9&&!hasLegal(lv);
function mateIn1(lv){const L=MV[lv],n=gen(L,false);
 for(let i=0;i<n;i++){make(L[i]);let r=false;if(att(ksq[side^1],side,false)>=1e9)r=isMate(lv+1);unmake();if(r)return true}return false}
function naive(lv){const L=MV[lv],n=gen(L,false),out=[];
 for(let i=0;i<n;i++){const m=L[i];make(m);
  if(att(ksq[side^1],side,false)<1e9){unmake();continue}
  const v=isMate(lv+1)?MATE:mateIn1(lv+1)?-MATE:-qs(-INF,INF,lv+1);unmake();out.push([v,m])}
 out.sort((x,y)=>y[0]-x[0]);return out.slice(0,3)}
function forkBonus(m){if((b[m&127]&7)===6)return 0;make(m);
 try{const us=side^1,th=side,to=(m>>7)&127,pc=b[to],pt=pc&7,pv=VAL[pt];
  if(att(to,th,true)<pv)return 0;let n=0;
  const chk=t=>{if(t&0x88)return;const c=b[t];if(!c||(c&8)===(us<<3))return;const ct=c&7;if(ct===6){n++;return}if(VAL[ct]>pv||att(t,th,false)>=1e9)n++};
  if(pt===1){chk(to+(us?-15:15));chk(to+(us?-17:17))}
  else if(pt===2)for(let k=0;k<8;k++)chk(to+KN[k]);
  else for(let g=0;g<2;g++){if(g===0&&pt===4)continue;if(g===1&&pt===3)continue;const D=g?RD:BD;
   for(let k=0;k<4;k++){let t=to+D[k];while(!(t&0x88)){if(b[t]){chk(t);break}t+=D[k]}}}
  return n>=2?45*(n-1):n===1?12:0}finally{unmake()}}
function trapBonus(m){ // ход m запирает фигуру соперника: она под боем и безопасных ходов нет (с учётом связок)
 make(m);let res=0;
 try{const us=side^1,th=side,rl=legalMoves();
  for(let q=0;q<128;q++){if(q&0x88){q+=7;continue}const p=b[q];if(!p||(p>>3)!==th)continue;const pt=p&7;if(pt<2||pt>5)continue;
   const aa=att(q,us,true);if(aa>=1e9)continue;
   if(!(aa<VAL[pt]||att(q,th,false)>=1e9))continue;
   let safe=false;
   for(const r of rl){if((r&127)!==q)continue;make(r);const t=(r>>7)&127,ok=att(t,us,true)>=VAL[pt]&&!(att(t,us,false)<1e9&&att(t,th,false)>=1e9);unmake();if(ok){safe=true;break}}
   if(!safe){let canTake=false;
    for(const r of rl){const t=(r>>7)&127;if(t===q)continue;if(b[t]&&(r&127)!==q){make(r);const still=att(q,us,true)<1e9;unmake();if(!still){canTake=true;break}}}
    if(!canTake)res=Math.max(res,Math.min(60,VAL[pt]/7|0))}}
 }finally{unmake()}
 return res}
function tactics(m){const w=side===0;if((b[m&127]&7)===6&&!((m>>17)&2))return{gain:0,risk:0};
 const ps0=pinsOf(w),po0=pinsOf(!w);make(m);const ps1=pinsOf(w),po1=pinsOf(!w);
 let risk=0;
 try{const rl=legalMoves();let mx=0;for(const r of rl){const fb=forkBonus(r);if(fb>mx)mx=fb}risk=mx}finally{unmake()}
 const gain=Math.max(0,Math.min(70,ps1-ps0)),exp=Math.max(0,Math.min(70,po1-po0));
 return{gain:gain,risk:Math.round(.8*risk)+exp}}
let skill=.5,adaptOn=true,lastTrap=null,BAN=null;
function trapValue(m,s,done){make(m);const rep=naive(2);
 if(!rep.length){unmake();return s}let tot=0,ws=0;
 const top=Math.max(-1000,Math.min(1000,rep[0][0]));
 for(let i=0;i<rep.length;i++){const a=Math.max(-1000,Math.min(1000,rep[i][0])),w=Math.max(.05,Math.exp((a-top)/150));
  make(rep[i][1]);let v=search(Math.max(1,done-2),-INF,INF,2,true);unmake();
  v=Math.max(-2500,Math.min(2500,v));tot+=w*v;ws+=w}
 unmake();return tot/ws}
function trapShape(m,cd){make(m);const rl=legalMoves();
 if(rl.length<4){unmake();return null}
 const vs=[],cp=[];let vmin=INF;
 for(const r of rl){const c=!!(b[(r>>7)&127]||((r>>17)&1)||((r>>14)&7));
  make(r);const v=search(Math.max(1,cd-3),-INF,INF,2,true);unmake();vs.push(v);cp.push(c);if(v<vmin)vmin=v}
 unmake();
 const good=new Set();let bad=0,quiet=0;
 for(let i=0;i<rl.length;i++){if(vs[i]<=vmin+60){good.add(rl[i]);if(!cp[i])quiet++}else if(vs[i]>vmin+150)bad++}
 const frac=good.size/rl.length;let bonus=0;
 if(bad/rl.length>=.4){bonus=frac<.12?35:frac<.25?20:frac<.4?8:0;if(bonus&&quiet===good.size)bonus+=15}
 return{bonus,good,frac,n:rl.length}}
function feedback(m){if(!lastTrap)return;const t=lastTrap;lastTrap=null;
 if(t.n<4||t.frac>.5)return;skill=.7*skill+.3*(t.good.has(m)?1:0)}
function rootSearch(d,rl){let a=-INF,best=-INF,bm=0;
 for(let i=0;i<rl.length;i++){make(rl[i]);let v;
  if(i===0)v=-search(d-1,-INF,-a,1,true);else{v=-search(d-1,-a-1,-a,1,true);if(v>a)v=-search(d-1,-INF,-a,1,true)}
  unmake();if(v>best){best=v;bm=rl[i]}if(v>a)a=v}
 return{m:bm,s:best}}
const sqn=s=>'abcdefgh'[s&7]+((s>>4)+1);
function name(m){const f=m&127,t=(m>>7)&127,p=b[f]&7,pr=(m>>14)&7,cap=b[t]||((m>>17)&1);
 if((m>>17)&2)return t>f?'O-O':'O-O-O';
 return(p>1?' PNBRQK'[p]:(cap?'abcdefgh'[f&7]:''))+(cap?'x':'')+sqn(t)+(pr?'='+'..NBRQ'[pr]:'')}
function think(D,tl,cun){const t0=Date.now(),sp0=sp;nodes=0;lastTrap=null;
 const unwind=()=>{while(sp>sp0)unmake()};
 let rl=legalMoves();if(!rl.length)return null;
 if(BAN){const fl=rl.filter(m=>!BAN.has(m));if(fl.length)rl=fl}
 const nm=new Map();for(const m of rl)nm.set(m,name(m));
 if(rl.length===1)return{m:rl[0],info:'Единственный ход '+nm.get(rl[0])};
 if(hm===0&&sp<2)for(let i=0;i<K1.length;i++){K1[i]=0;K2[i]=0}
 let best=rl[0],bs=0,done=0;
 try{
  for(let d=1;d<=D;d++){deadline=d===1?Infinity:t0+tl*.6;const r=rootSearch(d,rl);best=r.m;bs=r.s;done=d;
   rl.sort((x,y)=>(y===best)-(x===best));
   if(Math.abs(bs)>MATE-100||Date.now()-t0>tl*.4)break}
 }catch(e){if(e!==TO)throw e;unwind()}
 const base='глубина '+done+', узлов '+nodes+', оценка '+(bs>0?'+':'')+bs;
 if(done<3||Math.abs(bs)>MATE-100)return{m:best,info:'Лучший ход '+nm.get(best)+' ('+base+')'};
 const af=adaptOn?.5+(1-skill):1,margin=Math.round((bs<-150?150:bs>300?15:45)*cun/100*af),floor=bs-margin-1,cd=Math.max(2,done-3),cands=[];
 deadline=t0+tl*.78;
 try{for(const m of rl){make(m);const s=-search(cd,-INF,-floor,1,true);unmake();if(s>floor)cands.push([s,m])}}
 catch(e){if(e!==TO)throw e;unwind()}
 cands.sort((x,y)=>y[0]-x[0]);cands.length=Math.min(cands.length,8);
 if(cands.length<2)return{m:best,info:'Единственный разумный ход '+nm.get(best)+' ('+base+')'};
 const res=[];deadline=t0+tl*.97;
 try{for(const[s,m]of cands){const ex=trapValue(m,s,cd);let fb=forkBonus(m),tb=0,tc={gain:0,risk:0};
   if(res.length<6){tb=trapBonus(m);tc=tactics(m)}fb=Math.min(130,fb+tb+tc.gain)-tc.risk;let sh=null;if(res.length<3)sh=trapShape(m,cd);
   res.push({m,s,ex,fb,sh,f:.3*s+.7*ex+fb+(sh?sh.bonus:0)})}}
 catch(e){if(e!==TO)throw e;unwind()}
 if(!res.length)return{m:best,info:'Лучший ход '+nm.get(best)+' ('+base+')'};
 res.sort((x,y)=>y.f-x.f);const p=res[0];
 if(p.sh)lastTrap={good:p.sh.good,frac:p.sh.frac,n:p.sh.n};
 const info=p.m===best?'Лучший ход '+nm.get(best)+' совпал с ловушкой':
  'Ловушка: '+nm.get(p.m)+' вместо '+nm.get(best)+'. Ожидание против слабого ответа '+(p.ex>=0?'+':'')+Math.round(p.ex)+(p.fb?', вилки/связки/ловушки '+(p.fb>0?'+':'')+p.fb:'')+(p.sh&&p.sh.bonus?', единственный ответ +'+p.sh.bonus:'')+' (допуск '+margin+')';
 return{m:p.m,info:info+' ['+base+', анализ ловушек '+res.length+' из '+cands.length+(adaptOn?', точность игрока '+Math.round(skill*100)+'%':'')+']'}}
function perft(d){if(!d)return 1;const L=new Int32Array(256),n=gen(L,false);let c=0;
 for(let i=0;i<n;i++){make(L[i]);if(att(ksq[side^1],side,false)>=1e9)c+=perft(d-1);unmake()}return c}
function over(){const lm=legalMoves();
 if(!lm.length)return att(ksq[side],side^1,false)<1e9?(side===0?'Мат. Победили чёрные':'Мат. Победили белые'):'Пат. Ничья';
 if(hm>=100)return'Ничья по правилу 50 ходов';
 let rep=0;for(let i=sp-2;i>=0&&i>=sp-hm;i-=2)if(S_hh[i]===hh&&S_hl[i]===hl)rep++;if(rep>=2)return'Ничья повторением';
 let minors=0,heavy=false;for(let s=0;s<128;s++){if(s&0x88){s+=7;continue}const t=b[s]&7;if(t===1||t===4||t===5)heavy=true;else if(t===2||t===3)minors++}
 return!heavy&&minors<=1?'Ничья: недостаточно материала':''}
function sane(){const nk=[0,0],np=[0,0],tot=[0,0];
 for(let s=0;s<128;s++){if(s&0x88){s+=7;continue}const p=b[s];if(!p)continue;const c=p>>3,t=p&7;tot[c]++;if(t===6)nk[c]++;
  if(t===1){np[c]++;if((s>>4)===0||(s>>4)===7)return'пешка на крайней горизонтали'}}
 if(nk[0]!==1||nk[1]!==1)return'королей: белых '+nk[0]+', чёрных '+nk[1];
 if(np[0]>8||np[1]>8||tot[0]>16||tot[1]>16)return'слишком много фигур';
 if(Math.abs((ksq[0]&7)-(ksq[1]&7))<2&&Math.abs((ksq[0]>>4)-(ksq[1]>>4))<2)return'короли рядом';
 if(att(ksq[side^1],side,false)<1e9)return'король стороны без хода под шахом';
 return''}
const targets=f=>{const o=[];for(const m of legalMoves())if((m&127)===f&&!o.includes((m>>7)&127))o.push((m>>7)&127);return o};
setFen('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1');
return{setBan:a=>{BAN=a&&a.length?new Set(a):null},flags:o=>Object.assign(FLG,o),sane,cast:()=>cast,feedback,clearTrap:()=>{lastTrap=null},skill:()=>skill,setAdapt:v=>{adaptOn=v},setFen,perft,think,over,targets,moves:legalMoves,play:make,undo:unmake,get:s=>b[s],side:()=>side,hist:()=>sp,
 reset:()=>{lastTrap=null;setFen('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1')},
 inCheck:()=>att(ksq[side],side^1,false)<1e9,ksq:c=>ksq[c],last:()=>sp?{f:S_m[sp-1]&127,t:(S_m[sp-1]>>7)&127}:null,nodes:()=>nodes}})();
// Обработчик запросов к движку v2 (работает и в Worker, и в странице)
// Ведёт партию непрерывно: история, взятие на проходе, права рокировки, повторения.
var pending=null; // {m: наш последний ход, sp0: глубина стека до него}
var INIT='rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR';
function key(){var s='';for(var r=0;r<8;r++)for(var f=0;f<8;f++)s+=E.get(r*16+f)+',';return s}
function grid(pl){ // расстановка из FEN -> массив кодов [r*8+f], r=0 — первая горизонталь
 var g=[],r=7,f=0,i,ch;for(i=0;i<64;i++)g.push(0);
 for(i=0;i<pl.length;i++){ch=pl.charAt(i);
  if(ch==='/'){r--;f=0}else if(ch>='1'&&ch<='8')f+=+ch;
  else{var k='PNBRQKpnbrqk'.indexOf(ch);if(k<0||r<0||f>7)return null;g[r*8+f]=k<6?k+1:k-6+9;f++}}
 return g}
function gridKey(g){var s='';for(var i=0;i<64;i++)s+=g[i]+',';return s}
function placeCast(g){var c=0;
 if(g[4]===6){if(g[7]===4)c|=1;if(g[0]===4)c|=2}
 if(g[60]===14){if(g[63]===12)c|=4;if(g[56]===12)c|=8}
 return c}
function fenCast(f){if(f===undefined)return 15;var c=0;for(var i=0;i<f.length;i++){var x='KQkq'.indexOf(f.charAt(i));if(x>=0)c|=1<<x}return c}
function castStr(c){var s=(c&1?'K':'')+(c&2?'Q':'')+(c&4?'k':'')+(c&8?'q':'');return s||'-'}
function resync(fp,g,prevCast){
 var start=gridKey(g)===gridKey(grid(INIT)),c;
 if(start)c=15;else{c=fenCast(fp[2])&placeCast(g);if(prevCast>=0)c&=prevCast}
 E.setFen(fp[0]+' '+(fp[1]==='b'?'b':'w')+' '+castStr(c)+' - 0 1')}
function handle(q){
 try{
  E.setAdapt(!!q.adapt);
  var fp=String(q.fen).split(' '),g=grid(fp[0]);
  if(!g)return{id:q.id,error:'не удалось разобрать позицию'};
  var tg=gridKey(g),stm=fp[1]==='b'?1:0,cont=false,prevCast=-1;
  if(pending&&E.hist()===pending.sp0&&E.hist()<1000){
   prevCast=E.cast();
   if(key()===tg&&E.side()===stm)cont=true; // наш ход не состоялся — позиция та же
   else{
    E.play(pending.m);
    var lm=E.moves(),found=0,i;
    for(i=0;i<lm.length;i++){E.play(lm[i]);var ok=key()===tg&&E.side()===stm;E.undo();if(ok){found=lm[i];break}}
    if(found){E.feedback(found);E.play(found);cont=true}else E.undo();
   }
  }
  pending=null;
  if(!cont)resync(fp,g,prevCast);
  var bad=E.sane();
  if(bad){return{id:q.id,error:'Позиция выглядит неверно: '+bad}}
  var ov=E.over();
  if(ov&&(ov.charAt(0)==='М'||ov.charAt(0)==='П'||ov.indexOf('материала')>=0))return{id:q.id,none:true,over:ov};
  var sp0=E.hist(),bn=[];
  if(q.ban&&q.ban.length){var all=E.moves();
   for(var a=0;a<q.ban.length;a++)for(var z=0;z<all.length;z++){var mf=all[z]&127,mt=(all[z]>>7)&127,x=q.ban[a];
    if((mf&7)===x[0]&&(mf>>4)===x[1]&&(mt&7)===x[2]&&(mt>>4)===x[3])bn.push(all[z])}}
  E.setBan(bn);
  var r=E.think(q.depth,q.ms,q.cun);
  E.setBan(null);
  if(!r)return{id:q.id,none:true,over:'Нет ходов'};
  // защита от невозможного хода: ход обязан быть в списке легальных
  var lg=E.moves(),legal=false,j;for(j=0;j<lg.length;j++)if(lg[j]===r.m){legal=true;break}
  if(!legal){if(!lg.length)return{id:q.id,none:true,over:'Нет ходов'};r={m:lg[0],info:'Запасной ход (проверка легальности)'}}
  if(E.hist()!==sp0){pending=null;return{id:q.id,error:'внутреннее состояние движка сбилось'}}
  pending={m:r.m,sp0:sp0};
  var f=r.m&127,t=(r.m>>7)&127,pr=(r.m>>14)&7;
  return{id:q.id,ff:f&7,fr:f>>4,tf:t&7,tr:t>>4,pr:pr,info:r.info};
 }catch(err){pending=null;return{id:q.id,error:String(err)}}
}

const rl=require('readline').createInterface({input:process.stdin});
rl.on('line',l=>{const q=JSON.parse(l);const r=handle(q);process.stdout.write(JSON.stringify(r)+"\n");});
