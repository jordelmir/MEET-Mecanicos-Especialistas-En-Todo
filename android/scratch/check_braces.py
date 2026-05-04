
import sys

def check_braces(filename):
    with open(filename, 'r') as f:
        content = f.read()
    
    level = 0
    line_num = 1
    for char in content:
        if char == '{':
            level += 1
        elif char == '}':
            level -= 1
        if char == '\n':
            line_num += 1
        
        if level < 0:
            print(f"Extra closing brace at line {line_num}")
            level = 0 # reset to continue
            
    if level > 0:
        print(f"Missing {level} closing braces at end of file")
    elif level == 0:
        print("Braces are balanced")

if __name__ == "__main__":
    check_braces(sys.argv[1])
