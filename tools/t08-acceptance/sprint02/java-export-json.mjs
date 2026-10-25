const MAX_BYTES=2*1024*1024;
const fail=code=>{throw new Error(`JAVA_EXPORT_${code}`);};

/** 在对象校验前拒绝重复键；JSON.parse 会覆盖重复字段，不能独自充当契约边界。 */
export function parseJavaExportJson(bytes) {
  if(!Buffer.isBuffer(bytes))fail('FORMAT');
  if(bytes.length>MAX_BYTES)fail('LIMIT');
  let text;
  try {text=new TextDecoder('utf-8',{fatal:true}).decode(bytes);}
  catch {fail('FORMAT');}
  let index=0;
  const whitespace=()=>{while(/[\x20\t\r\n]/.test(text[index]??'!'))index++;};
  function string() {
    const start=index++;
    while(index<text.length){
      const ch=text[index++];
      if(ch==='"'){
        try {return JSON.parse(text.slice(start,index));}catch {fail('FORMAT');}
      }
      if(ch==='\\')index++;
    }
    fail('FORMAT');
  }
  function value(depth) {
    whitespace();const ch=text[index];
    if(ch==='{'||ch==='['){
      if(depth>=24)fail('LIMIT');
      const object=ch==='{',end=object?'}':']',keys=new Set();index++;whitespace();
      if(text[index]===end){index++;return;}
      while(index<text.length){
        if(object){
          if(text[index]!=='"')fail('FORMAT');const key=string();
          if(['__proto__','prototype','constructor'].includes(key))fail('UNSAFE_KEY');
          if(keys.has(key))fail('DUPLICATE_KEY');keys.add(key);whitespace();
          if(text[index++]!==':')fail('FORMAT');
        }
        value(depth+1);whitespace();
        if(text[index]===end){index++;return;}
        if(text[index++]!==',')fail('FORMAT');whitespace();
      }
      fail('FORMAT');
    }
    if(ch==='"'){string();return;}
    const token=/^(?:true|false|null|-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)/.exec(text.slice(index));
    if(!token)fail('FORMAT');index+=token[0].length;
  }
  value(0);whitespace();if(index!==text.length)fail('FORMAT');
  try {return JSON.parse(text);}catch {fail('FORMAT');}
}
