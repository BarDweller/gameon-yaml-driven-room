package org.ozzy.runtime;

import java.util.HashMap;
import java.util.Map;

public class ConditionParser {
	
	public static class ParseException extends Exception{
		private static final long serialVersionUID = 1L;
		public ParseException(String reason) {
			super(reason);
		}
	}

	private static abstract class Expression {
		abstract boolean evaluate();
	}

	private static class Evaluation extends Expression {
		String lhs;
		String operator;
		String rhs;

		boolean evaluate() {
			if ("==".equals(operator)) {
				return lhs.equals(rhs);
			}else if ("!=".equals(operator)) {
				return !lhs.equals(rhs);
			}
			return false;
		}
	}

	private static class AndExpression extends Expression {
		Expression a;
		Expression b;

		boolean evaluate() {
			return a.evaluate() && b.evaluate();
		}
	}

	private static class OrExpression extends Expression {
		Expression a;
		Expression b;

		boolean evaluate() {
			return a.evaluate() || b.evaluate();
		}
	}

	private static class State {
		String expression;
		int idx;

		public State(String expression) {
			this.expression = expression.trim()+" ";
			this.idx = 0;
		}
	}

	//left to right parser, aggregates each part into the lhs of whatever comes next.
	private Expression parse(State state) throws ParseException{
		String currentToken = null;
		Evaluation eval = new Evaluation();
		Expression result = null;
		boolean inQuote=false;
		while (state.idx < state.expression.length()) {
			char next = state.expression.charAt(state.idx);
			if(inQuote) {
				if(next == '"') {
					inQuote=false;
				}else {
					if(currentToken==null) {
						currentToken="";
					}
					currentToken += next;
				}
			}else {
				switch (next) {
				case '"': {
					inQuote=true;
					currentToken="";
					break;
				}
				case ' ': {
					// end of block
					if (currentToken!=null) {
						if (eval.lhs == null) {
							eval.lhs = currentToken;
							currentToken=null;
						} else if (eval.rhs == null) {
							eval.rhs = currentToken;
							currentToken=null;
							if (result == null) {
								result = eval;
							} else if (result instanceof AndExpression) {
								if (((AndExpression) result).b == null) {
									((AndExpression) result).b = eval;
								} else {
									throw new ParseException("ERROR: expression [" + state.expression + "] unexpected evaluation after && usage in " + state.expression+" Eg, you can't do 'a==b && c==d e==f'");
								}
							} else if (result instanceof OrExpression) {
								if (((OrExpression) result).b == null) {
									((OrExpression) result).b = eval;
								} else {
									throw new ParseException("ERROR: expression [" + state.expression + "] unexpected evaluation after || usage in " + state.expression+" Eg, you can't do 'a==b || c==d e==f'");
								}
							}
							eval = new Evaluation();
						} else {
							throw new ParseException("ERROR: expression [" + state.expression + "] malformed. You can't do 'a==1 b==2'");
						}
					}
					break;
				}
				case '=': {
					if (eval.lhs == null) {
						eval.lhs = currentToken;
						currentToken="";
					}
					// == maybe
					if (state.expression.length() > (state.idx)) {
						if (state.expression.charAt(state.idx + 1) == '=') {
							if (eval.lhs == null) {
								throw new ParseException("ERROR: expression [" + state.expression + "] missing lhs for ==, Eg, you cannot do '==b'");
							} else {
								// found==
								eval.operator = "==";
								state.idx++;
							}
						}else {
							throw new ParseException("ERROR: expression [" + state.expression + "] used = but not == Eg. 'a=1'. Expected '=' but found' "+state.expression.charAt(state.idx + 1)+"'");
						}
					} else {
						throw new ParseException("ERROR: expression [" + state.expression + "] unmatched = at end of expression. Eg 'a='");
					}
					break;
				}
				case '!': {
					if (eval.lhs == null) {
						eval.lhs = currentToken;
						currentToken="";
					}
					// negation
					// != maybe
					if (state.expression.length() > (state.idx)) {
						if (state.expression.charAt(state.idx + 1) == '=') {
							if (eval.lhs == null) {
								throw new ParseException("ERROR: expression [" + state.expression + "] missing lhs for !=, Eg, you cannot do '!=b'");
							} else {
								// found!=
								eval.operator = "!=";
								state.idx++;
							}
						}else {
							throw new ParseException("ERROR: expression [" + state.expression + "] used ! but not != Eg. 'a!1'. Expected '=' but found' "+state.expression.charAt(state.idx + 1)+"'");
						}
					} else {
						throw new ParseException("ERROR: expression [" + state.expression + "] unmatched ! at end of expression. Eg 'a!'");
					}
					break;
				}
				case '&': {
					// &&
					if (state.expression.length() > (state.idx)) {
						if (state.expression.charAt(state.idx + 1) == '&') {
							if (result == null) {
								System.out.println("ERROR: expression [" + state.expression + "] missing lhs for &&, Eg you cannot do '&& a==b'");
							} else {
								// found &&
								AndExpression and = new AndExpression();
								and.a = result;
								result = and;
								state.idx++;
							}
						}else {
							throw new ParseException("ERROR: expression [" + state.expression + "] used & but not && Eg. 'a==1 & b==2'. Expected '&' but found' "+state.expression.charAt(state.idx + 1)+"' ");
						}
					} else {
						throw new ParseException("ERROR: expression [" + state.expression + "] unmatched & at end of expression. Eg 'a==1 &'");
					}
					break;
				}
				case '|': {
					// ||
					if (state.expression.length() > (state.idx)) {
						if (state.expression.charAt(state.idx + 1) == '|') {
							if (result == null) {
								System.out.println("ERROR: expression [" + state.expression + "] missing lhs for ||");
							} else {
								// found ||
								OrExpression or = new OrExpression();
								or.a = result;
								result = or;
								state.idx++;
							}
						}else {
							throw new ParseException("ERROR: expression [" + state.expression + "] used | but not || Eg. 'a==1 | b==2'. Expected '|' but found' "+state.expression.charAt(state.idx + 1)+"' ");
						}
					} else {
						throw new ParseException("ERROR: expression [" + state.expression + "] unmatched | at end of expression. Eg 'a==1 |'");
					}
					break;
				}
				default: {
					if(currentToken==null) {
						currentToken="";
					}					
					currentToken += next;
				}
				}
			}
			state.idx++;
		}
		if(inQuote)
			throw new ParseException("ERROR: expression [" + state.expression + "]  unbalanced quote, parser ended up still inside quoted string at end of expression");
		return result;

	}

	public ConditionParser() {
	}
	
	//swap in state/var values to enable comparisons.
	private String substituteVars(String exp, Map<String,Object> stateById, String args, String playerId, String playerName) {
			String fixed=exp.trim();
			if((fixed.startsWith("\"") && fixed.endsWith("\"")) || 
				(fixed.startsWith("'") && fixed.endsWith("'"))	) {
				fixed = fixed.substring(1,fixed.length()-1);
			}
			//sub in vars
			for(Map.Entry<String,Object> kv : stateById.entrySet()) {
				fixed=fixed.replace(kv.getKey(), kv.getValue().toString());
			}
			fixed=fixed.replace("{arg}", args);
			fixed=fixed.replace("{id}", playerId);
			fixed=fixed.replace("{name}", playerName);			
			return fixed;
	}
		
	//duplicate an expression, but swap template values for their current values from state.
	private Expression copyExpressionAndFillInVars(Expression expression, Map<String,Object> stateById, String args, String playerId, String playerName) 
	throws ParseException{
		if(expression==null) {
			throw new ParseException("ERROR: Internal: Badly parsed expression resulted in null discovered during template resolution.");
		}
		if(expression instanceof Evaluation) {
			Evaluation e = new Evaluation();
			e.operator = ((Evaluation) expression).operator;
			e.lhs = substituteVars(((Evaluation) expression).lhs, stateById, args, playerId, playerName);
			e.rhs = substituteVars(((Evaluation) expression).rhs, stateById, args, playerId, playerName);
			
			if(e.lhs.contains("{") ) {
				throw new ParseException("ERROR: Unable to satisfy all template vars requested in expression. Remaining: "+e.lhs);
			}
			if(e.rhs.contains("{") ) {
				throw new ParseException("ERROR: Unable to satisfy all template vars requested in expression. Remaining: "+e.rhs);
			}
			
			return e;
		}else if(expression instanceof AndExpression) {
			AndExpression ae = new AndExpression();
			ae.a = copyExpressionAndFillInVars(((AndExpression) expression).a, stateById, args, playerId, playerName);
			ae.b = copyExpressionAndFillInVars(((AndExpression) expression).b, stateById, args, playerId, playerName);
			return ae;
		}else if(expression instanceof OrExpression) {
			OrExpression oe = new OrExpression();
			oe.a = copyExpressionAndFillInVars(((OrExpression) expression).a, stateById, args, playerId, playerName);
			oe.b = copyExpressionAndFillInVars(((OrExpression) expression).b, stateById, args, playerId, playerName);
			return oe;
		}
		throw new ParseException("ERROR: Internal: Unknown Expression Subclass "+expression.getClass().getCanonicalName());
	}
	
	// evaluate an expression using current state.
	public boolean evaluate(String expression, Map<String,Object> stateById, String args, String playerId, String playerName) {
		if(args==null)args="";
		
		if("unmatched".equals(expression.trim())){
			return true;
		}
		
		State s = new State(expression);
		try {
			Expression e = parse(s);
			
			//expression is complete, but still templatized / holding var references.
			e = copyExpressionAndFillInVars(e, stateById, args, playerId, playerName);
			
			return e.evaluate();
		}catch(ParseException pe) {
			System.out.println("ERROR: parsing: "+expression);
			throw new RuntimeException(pe);
		}
	}
	
	// test rig!!
	public static void main(String args[]) {
		ConditionParser cp = new ConditionParser();
		
		String t1 = "a==1 && b==2 && c==3";
		String t2 = "a==1 && b==2 || c==3";
		String t3 = "a==\"fish\"";
		
		Map<String,Object> stateById = new HashMap<String,Object>();

		stateById.put("a", "fish");
		
		System.out.println(cp.evaluate(t3, stateById, "", "john:1", "John"));

		
	}
}
