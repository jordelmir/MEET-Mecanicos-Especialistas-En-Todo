const fs = require('fs');
const path = require('path');

const replacements = [
  { search: /rgba\(232,\s*160,\s*32,/g, replace: 'rgba(0, 240, 255,' },
  { search: /#e8a020/g, replace: '#00f0ff' },
  { search: /#c98a10/g, replace: '#00c2cf' }
];

function walk(dir) {
  let results = [];
  const list = fs.readdirSync(dir);
  list.forEach(file => {
    file = path.join(dir, file);
    const stat = fs.statSync(file);
    if (stat && stat.isDirectory() && !file.includes('node_modules') && !file.includes('.git') && !file.includes('dist')) {
      results = results.concat(walk(file));
    } else if (file.endsWith('.ts') || file.endsWith('.tsx')) {
      results.push(file);
    }
  });
  return results;
}

const files = walk('.');
let changedFiles = 0;

files.forEach(file => {
  let content = fs.readFileSync(file, 'utf8');
  let newContent = content;
  replacements.forEach(r => {
    newContent = newContent.replace(r.search, r.replace);
  });
  if (content !== newContent) {
    fs.writeFileSync(file, newContent, 'utf8');
    changedFiles++;
    console.log('Updated:', file);
  }
});
console.log('Total files changed:', changedFiles);
