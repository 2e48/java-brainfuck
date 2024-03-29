
package application;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.HighlightPainter;

public class Interpreter {
    //Logging Importance levels
    private final int LOW = 1;
    private final int NORMAL = 2;
    private final int HIGH = 4;
    private final int SEVERE = 8;
    private final int PRIORITY = 128; //this should be the cap
    
    //Logging level
    private final int verbosity;
    
    //Code and Input strings
    private String code;
    private String input;
    
    //Interpreter pointers
    private int memorySize = 1;
    private int memoryPointer = 0;
    private int codePointer = 0;
    private int inputPointer = 0;
    
    //some misc lol
    private int prevMemorySize = 0;
    
    //Interpreter Loop management
    private LoopIndex loopIndex;
    
    //Interpreter Settings, non-realtime
    private boolean usingNegatives = false;
    private boolean usingWrapping = true;
    private int maxCellSize = 256;
    
    //Interpreter settings, realtime
    private int delay = 0;
    
    //Check if the code is running
    private boolean isRunning = false;
    
    //Status related variables
    private int steps = 0;
    private String status;
    
    //Components to use
    private java.awt.List memoryTape;
    private JTextArea textInput;
    private JTextArea textOutput;
    private JTextField textUserInput;
    
    //For highlighting
    private final HighlightPainter painter =
            new DefaultHighlighter.DefaultHighlightPainter(java.awt.Color.BLUE);
    private Highlighter highlighter;
    
    public Interpreter(int verbosity) {
        this.verbosity = verbosity;        
        log("Intrepreter initialized (verbosity: " 
                + verbosityLevel(this.verbosity) 
                + ")"
            , PRIORITY
        );
    }
    
    public Interpreter() {
        this.verbosity = 2;
        log("Intrepreter initialized (verbosity: " 
                + verbosityLevel(this.verbosity) 
                + ")"
            , PRIORITY
        );
    }

    public void setCode(String code) {
        if(!isRunning)
            this.code = code;
    }
    
    public void setInput(String input) {
        if(!isRunning)
            this.input = input;
    }
    
    public void start() throws BadLocationException {
        if(scanForLoops(code)) {
            doInterpretation();
        } else {
            log("Mismatched loop brackets", SEVERE);
        }
    }
    
    public String getFinishingStatus() {
        String message = status;
        
        if(steps > 0) {
            message += String.format(" [Ran through %d instructions, used %d cells]", steps, prevMemorySize);
        }
        
        return message;
    }
    
    public void stop() {
        log("Stopped.", SEVERE);
        
        reset();
    }
    
    private void reset() {
        isRunning = false;
        prevMemorySize = memorySize;
        memorySize = 1;
        memoryPointer = 0;
        codePointer = 0;
        inputPointer = 0;
        
        highlighter.removeAllHighlights();
        
        loopIndex = null;
    }
    
    // <editor-fold defaultstate="collapsed" desc="Interpreter Initialization">
    private void doInterpretation() throws BadLocationException {
        log("\nExecuting instructions...", SEVERE);        
        isRunning = true;
        
        highlighter = textInput.getHighlighter();
        
        while(codePointer < code.length() && isRunning) {
            
            char c = code.charAt(codePointer);
            switch(c) {
                case '+': 
                    inc(); 
                    break;
                case '-': 
                    dec(); 
                    break;
                case '>': 
                    movfwd(); 
                    break;
                case '<': 
                    movbck(); 
                    break;
                case '[': 
                    jmpfwd(); 
                    break;
                case ']': 
                    jmpbck(); 
                    break;
                case '.': 
                    output(); 
                    break;
                case ',': 
                    input(); 
                    break;
                default: 
                    codePointer += 1; 
                    continue;
            } 
            codePointer += 1;
            steps += 1;
            
            highlightText(codePointer);
            
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                Logger.getLogger(Interpreter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        isRunning = false;
        log("\nFinished Excecution", SEVERE);
        reset();
    }    
    
    private boolean scanForLoops(String code) {
        log("\nScanning for loops...", NORMAL);
        List<Integer> open = new ArrayList<>();
        List<Integer> close = new ArrayList<>();
        
        for (int i = 0; i < code.length(); i += 1) {
            if(code.charAt(i) == '['){
                log("[ @ " + i, LOW);
                open.add(i);
            } //if
        } //for
        
        try {
            log("=Finding pairs=", LOW);
            open.forEach((openIndex) -> {
                int openBrackets = 0;
                
                log(addChars("Ff: [ @ " + openIndex, openBrackets + 1, "-", true), LOW);
                
                for (int i = openIndex + 1; i < code.length(); i += 1) {
                    if(code.charAt(i) == '[') {
                        openBrackets += 1;
                        log(addChars("[ @ " + i, openBrackets + 1, "-", true), LOW);
                    } else if(code.charAt(i) == ']') {
                        if(openBrackets > 0) {
                            log(addChars("] @ " + i, openBrackets + 1, "-", true), LOW);
                            openBrackets -= 1;
                        } else {
                            close.add(i);
                            log(addChars("[] @ " + i, openBrackets + 1, "-", true), LOW);
                            break;
                        }
                    }
                }
            });
        } catch (Exception e) {
            log("Empty opening loop", NORMAL);
        }//try catch       
        
        log("O:" + open, LOW);
        log("C:" + close, LOW);
        
        if(open.size() != close.size()) {
            log("Mismatched loop brackets", NORMAL);
            return false;
        } else {
            loopIndex = new LoopIndex(open, close);        
            return true;
        }
    } //scanForLoops
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Brainfuck instructions">
    private void inc() {
        log("+: Incrementing cell " + memoryPointer, LOW);
        int value;
        try {
           value = Integer.parseInt(memoryTape.getItem(memoryPointer));

           value += 1;

           if(usingNegatives) {
               if(usingWrapping && value == (maxCellSize / 2)) {
                   value = -(maxCellSize / 2);
               }
           } else {
               if(usingWrapping && value == maxCellSize) {
                   value = 0;
               }
           }

           memoryTape.replaceItem("" + value, memoryPointer);
        } catch (Exception e) {
            memoryTape.add(String.valueOf(1));
        }

        memoryTape.select(memoryPointer);
    }
    
    private void dec() {
        log("-: Decrementing cell " + memoryPointer, LOW);
        int value;
        try {
           value = Integer.parseInt(memoryTape.getItem(memoryPointer));

           value -= 1;

           if(usingNegatives) {
               if(usingWrapping && value == -((maxCellSize / 2) + 1)) {
                   value = (maxCellSize / 2) - 1;
               }
           } else {
               if(usingWrapping && value == -1) {
                   value = maxCellSize - 1;
               }
           }

           memoryTape.replaceItem("" + value, memoryPointer);
        } catch (Exception e) {
            memoryTape.add(String.valueOf(maxCellSize - 1));
        }

        memoryTape.select(memoryPointer);
    }
    
    private void movfwd() {
        log(">: Moving forward to cell " + (memoryPointer + 1), LOW);
        memoryPointer += 1;

        if (memoryPointer == memorySize) {
            memoryTape.add(0 + "");
            memorySize += 1;
        }
    }
    
    private void movbck() {
        log("<: Moving backward to cell " + (memoryPointer - 1), LOW);
        memoryPointer -= 1;

        if (memoryPointer < 0) {
            memoryTape.add(0 + "", 0);
            memoryPointer = 0;
            memorySize += 1;
        }
    }
    
    private void jmpfwd() {
        int value = Integer.parseInt(memoryTape.getItem(memoryPointer)); 
        
        if(value == 0) {
            codePointer = loopIndex.getClosingPair(codePointer);
            log("[: Jumping forward to " + codePointer, LOW);
        }
    }
    
    private void jmpbck() {
        int value = Integer.parseInt(memoryTape.getItem(memoryPointer)); 
        
        if(value != 0) {
            codePointer = loopIndex.getOpeningPair(codePointer);
            log("]: Jumping back to " + codePointer, LOW);
        }
    }
    
    private void output() {
        int value = Integer.parseInt(memoryTape.getItem(memoryPointer));         
        char ascii = (char) value;
        log(".: Outputting \"" + ascii + "\"", LOW);
        
        textOutput.append(String.valueOf(ascii));
        textOutput.setCaretPosition(textOutput.getText().length());
    }
    
    private void input() {
        if(inputPointer > input.length() - 1) {
            memoryTape.replaceItem(String.valueOf(0), memoryPointer);
        } else {
            int i = input.charAt(inputPointer);

            if(i >= maxCellSize) i %= maxCellSize;

            memoryTape.replaceItem(String.valueOf(i), memoryPointer);
            log(",: Adding \"" + i + "\" to memory", LOW);
        }

        inputPointer += 1;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Interpreter Settings setting."> 
    public void setComponents(java.awt.List memoryTape, JTextArea codeInput, JTextArea output, JTextField userInput) {
        log("Setting components...", LOW);
        log("\nComponents found:"
                + "\nmemoryTape:" + memoryTape
                + "\ncodeInput: " + codeInput
                + "\noutput: " + output
                + "\nuserInput: " + userInput
            , LOW
        );
        this.memoryTape = memoryTape;
        this.textInput = codeInput;
        this.textOutput = output;
        this.textUserInput = userInput;
        log("\nComponents saved:"
                + "\nmemoryTape:" + this.memoryTape
                + "\ncodeInput: " + this.textInput
                + "\noutput: " + this.textOutput
                + "\nuserInput: " + this.textUserInput
            , LOW
        );
    }    
    
    public void setSettings(boolean usingNegatives, boolean usingWrapping, int maxCellSize) {
        log("\nSetiing options...", LOW);
        this.usingNegatives = usingNegatives;
        this.usingWrapping = usingWrapping;
        this.maxCellSize = maxCellSize;
    }    
    
    public void setDelay(int delay) {
        log("\nSetting delay to " + delay, LOW);
        this.delay = delay;
    }
    // // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Classes">
    private class LoopIndex {
        private final List<Integer> openBracket;
        private final List<Integer> closeBracket;
        
        public LoopIndex(List<Integer> openList, List<Integer> closeList) {
            log("Saving loop locations", LOW);
            openBracket = openList;
            closeBracket = closeList;
            log("O:" + openList + "\nC:" + closeList, LOW);
        }
        
        public int getOpeningPair(int position) {
            return openBracket.get(closeBracket.indexOf(position));
        }
        
        public int getClosingPair(int position) {
            return closeBracket.get(openBracket.indexOf(position));            
        }
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Misc functions">
    private void log(Object msg, int importance) {        
        if(importance >= verbosity)
            System.out.println(String.valueOf(msg));
        
        if(importance > LOW)
            status = String.valueOf(msg);
    }    
    
    private String addChars(String str, int pad, String ins, boolean atLeft) {
        String offset = "";
        
        for (int i = 0; i < pad; i += 1) {
            offset += ins;
        }
        
        if(atLeft) {
            return offset + str;
        }
        
        return str + offset;
    }
    
    private String verbosityLevel(int level) {
        if(level <= LOW) return "LOW";
        else if(level <= NORMAL) return "NORMAL";        
        else if(level <= HIGH) return "HIGH";        
        else if(level <= SEVERE) return "SEVERE";        
        return "PRIORITY";
    }
    
    private void highlightText(int pos) throws BadLocationException {        
        highlighter.removeAllHighlights();
         
        highlighter.addHighlight(pos, pos + 1, painter);
        
        textInput.setCaretPosition(pos);
    }
    // </editor-fold>    
}
