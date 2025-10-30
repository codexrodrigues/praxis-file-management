#!/usr/bin/env node
const fs=require('fs');
const path=require('path');
const {execSync}=require('child_process');
const root=path.join(__dirname,'..');
const files=execSync("rg -l '@ConfigurationProperties' -g '*.java' praxis-files-core praxis-files-starter",{cwd:root}).toString().trim().split('\n').filter(Boolean);
function hyphenate(str){return str.replace(/([a-z])([A-Z])/g,'$1-$2').toLowerCase();}
const entries=[];
function parse(file,prefix){
 const lines=fs.readFileSync(file,'utf8').split(/\r?\n/);
 const stack=[prefix];
 let comment='';
 let inComment=false;
 for(let raw of lines){
  const line=raw.trim();
  if(line.startsWith('/**')){inComment=true;comment='';continue;}
  if(inComment){
    if(line.startsWith('*/')){inComment=false;comment=comment.trim().replace(/\s+/g,' ');continue;}
    comment+=line.replace(/^\* ?/,'')+' ';
    continue;
  }
  const classMatch=line.match(/^(public\s+)?static\s+class\s+(\w+)/)||line.match(/^class\s+(\w+)/);
  if(classMatch){stack.push(hyphenate(classMatch[2]||classMatch[1]));continue;}
  if(line=='}'&&stack.length>1){stack.pop();continue;}
  const fieldMatch=line.match(/^private\s+(?:final\s+)?[\w<>,\.]+\s+(\w+)(\s*=\s*([^;]+))?;/);
  if(fieldMatch){const name=fieldMatch[1];const def=fieldMatch[3]?fieldMatch[3].trim():'';entries.push({property:stack.join('.')+'.'+hyphenate(name),default:def,description:comment});comment='';}
 }
}
for(const rel of files){
 const file=path.join(root,rel);
 const content=fs.readFileSync(file,'utf8');
 const m=content.match(/@ConfigurationProperties\(prefix\s*=\s*"([^"]+)"/);
 if(!m)continue;
 parse(file,m[1]);
}
entries.sort((a,b)=>a.property.localeCompare(b.property));
const table=['| Property | Default | Description |','|----------|---------|-------------|'];
for(const e of entries){const def=e.default?e.default.replace(/"/g,'`').replace(/\s+/g,' '):'';table.push(`| \`${e.property}\` | \`${def}\` | ${e.description} |`);}
fs.writeFileSync(path.join(root,'CONFIG_MATRIX.md'),table.join('\n')+'\n');
const readmePath=path.join(root,'README.md');
const start='<!-- CONFIG_TABLE:START -->';
const end='<!-- CONFIG_TABLE:END -->';
let readme=fs.readFileSync(readmePath,'utf8');
const startIdx=readme.indexOf(start);
const endIdx=readme.indexOf(end);
if(startIdx!==-1&&endIdx!==-1){
  const before=readme.slice(0,startIdx+start.length);
  const after=readme.slice(endIdx);
  readme=before+'\n'+table.join('\n')+'\n'+after;
  fs.writeFileSync(readmePath,readme);
}
