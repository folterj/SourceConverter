package yost.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SourceConverter {

	private String filename = "background_subtractor";
	private String tripleQuote = "\"\"\"";
	
	
	public SourceConverter() {
		convertPythonToCpp(filename);
	}
	
	private void convertPythonToCpp(String filename) {
		String outHpp = "";
		String outCpp = "";
		String output = "";
		String content = readFile(filename + ".py");
		String token = "";
		int findent = 0;
		int indent;
		int lastIndent = 0;
		boolean inLineComment = false;
		boolean inComment = false;
		boolean lineContinue = false;
		boolean startLine = true;
		boolean extraBracket = false;
		int bracketDepth = 0;
		
		for (char c : content.toCharArray()) {
			if (startLine && (c == ' ' || c == '\t')) {
				// indentation
				if (c == ' ') {
					findent += 1;
				} else if (c == '\t') {
					findent += 4;
				}
				token += c;
			} else {
				if (startLine && c != '\r' && c != '\n') {
					// finish indentation
					indent = (int)Math.ceil(findent / 4.0);
					startLine = false;
					if (!lineContinue && bracketDepth == 0) {
						while (lastIndent > indent) {
							lastIndent--;
							output += "\t".repeat(lastIndent) + "}\n";
						}
					}
					output += token;
					token = "";
				}
				if (!inComment && !inLineComment) {
					if (c == '(' || c == '[' || c == '{') {
						bracketDepth++;
					} else if (c == ')' || c == ']' || c == '}') {
						bracketDepth--;
					}
				}
				if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
					if (token.equals(tripleQuote)) {
						inComment = !inComment;
						if (inComment) {
							token = "/*";
						} else {
							token = "*/";
						}
					}
					if (!inComment && !inLineComment && bracketDepth == 0 && !token.isBlank() && !token.equals("*/")) {
						if (token.equals("def")) {
							token = "void";
						}
						
						if (token.equals("else") && extraBracket) {
							token = ") " + token;
							extraBracket = false;
						}
						
						if (token.equals("if") || token.equals("elif") || token.equals("for") || token.equals("while")) {
							if (token.equals("elif")) {
								token = "else if";
							}
							token += " (";
							extraBracket = true;
						}

						lineContinue = token.endsWith("\\");
						if (lineContinue) {
							token = token.substring(0, token.length() - 1);
						} else if (token.endsWith(":")) {
							lastIndent++;
							token = token.substring(0, token.length() - 1);
							if (extraBracket) {
								token += " )";
								extraBracket = false;
							}
							token += " {";
						} else if (c == '\r' || c == '\n') {
							token += ";";
						}
					}
					
					output += token + c;
					token = "";
				} else if (c == '#') {
					token += "//";
					inLineComment = true;
				} else {
					token += c;
				}
				if (c == '\r' || c == '\n') {
					// reset
					findent = 0;
					startLine = true;
					inLineComment = false;
				}
			}
		}
		
		output += token;
		while (lastIndent > 0) {
			lastIndent--;
			output += "\t".repeat(lastIndent) + "}\n";
		}
		
		output = lineOperations(output);
		
		// * TODO: sort properties/methods into h/cpp from general output
		
		//writeFile(outHpp, filename + ".h");
		//writeFile(outCpp, filename + ".cpp");
		writeFile(output, filename + ".cpp");
	}

	private String lineOperations(String input) {
		String output = "";
		String[] lines = input.split("\\R");
		String tline;
		int j;
		boolean inComment = false;
		
		for (int i = 0; i < lines.length; i++) {
			tline = lines[i].trim();
			if (tline.equals(tripleQuote)) {
				inComment = !inComment;
			}
			if (!inComment) {
				if (tline.startsWith("for")) {
					lines[i] = createForLoop(lines[i]);
				}
				if (tline.equals("}")) {
					j = i - 1;
					while (lines[j].isBlank()) {
						lines[j] = lines[j + 1];
						lines[j + 1] = "";
						j--;
					}
				}
			}
		}
		for (String line : lines) {
			output += line + System.lineSeparator();
		}
		output = output.replace("self.", "").replace("self, ", "").replace("self,", "").replace("(self)", "()");
		
		return output;
	}
	
	private String createForLoop(String input) {
		String output;
		int start = input.indexOf("for");
		int end = input.lastIndexOf(")");
		String[] parts = input.split("[\\(||\\)]");
		String[] params = parts[1].trim().split(" ");
		String param = params[0].trim();
		String[] range = parts[2].trim().split(",");
		String range1, range2;
		if (range.length > 1) {
			range1 = range[0].trim();
			range2 = range[1].trim();
		} else {
			range1 = "0";
			range2 = range[0].trim();
		}
		
		output = input.substring(0, start) + "for ( " + param + " = " + range1 + "; " + param + " < " + range2 + "; " + param + "++ )" + input.substring(end + 1);
		
		return output;
	}

	private String readFile(String filename) {
		try {
			return Files.readString(Paths.get(filename));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	private void writeFile(String content, String filename) {
		try {
			Files.writeString(Paths.get(filename), content);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new SourceConverter();
		System.exit(0);
	}

}
