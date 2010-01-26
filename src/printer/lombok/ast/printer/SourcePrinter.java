/*
 * Copyright © 2010 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.ast.printer;

import static lombok.ast.printer.SourceFormatter.FAIL;

import lombok.ast.AlternateConstructorInvocation;
import lombok.ast.Annotation;
import lombok.ast.AnnotationDeclaration;
import lombok.ast.AnnotationElement;
import lombok.ast.AnnotationMethodDeclaration;
import lombok.ast.ArrayAccess;
import lombok.ast.ArrayCreation;
import lombok.ast.ArrayDimension;
import lombok.ast.ArrayInitializer;
import lombok.ast.Assert;
import lombok.ast.BinaryExpression;
import lombok.ast.Block;
import lombok.ast.BooleanLiteral;
import lombok.ast.Break;
import lombok.ast.Case;
import lombok.ast.Cast;
import lombok.ast.Catch;
import lombok.ast.CharLiteral;
import lombok.ast.ClassDeclaration;
import lombok.ast.ClassLiteral;
import lombok.ast.Comment;
import lombok.ast.CompilationUnit;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.ConstructorInvocation;
import lombok.ast.Continue;
import lombok.ast.Default;
import lombok.ast.DoWhile;
import lombok.ast.EmptyStatement;
import lombok.ast.EnumConstant;
import lombok.ast.EnumDeclaration;
import lombok.ast.ExpressionStatement;
import lombok.ast.FloatingPointLiteral;
import lombok.ast.For;
import lombok.ast.ForEach;
import lombok.ast.ForwardingASTVisitor;
import lombok.ast.Identifier;
import lombok.ast.If;
import lombok.ast.ImportDeclaration;
import lombok.ast.InlineIfExpression;
import lombok.ast.InstanceInitializer;
import lombok.ast.InstanceOf;
import lombok.ast.IntegralLiteral;
import lombok.ast.InterfaceDeclaration;
import lombok.ast.KeywordModifier;
import lombok.ast.LabelledStatement;
import lombok.ast.ListAccessor;
import lombok.ast.MethodDeclaration;
import lombok.ast.MethodInvocation;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.NullLiteral;
import lombok.ast.PackageDeclaration;
import lombok.ast.Return;
import lombok.ast.Select;
import lombok.ast.StaticInitializer;
import lombok.ast.StringLiteral;
import lombok.ast.Super;
import lombok.ast.SuperConstructorInvocation;
import lombok.ast.Switch;
import lombok.ast.Synchronized;
import lombok.ast.This;
import lombok.ast.Throw;
import lombok.ast.Try;
import lombok.ast.TypeArguments;
import lombok.ast.TypeBody;
import lombok.ast.TypeReference;
import lombok.ast.TypeReferencePart;
import lombok.ast.TypeVariable;
import lombok.ast.UnaryExpression;
import lombok.ast.UnaryOperator;
import lombok.ast.VariableDeclaration;
import lombok.ast.VariableDefinition;
import lombok.ast.VariableDefinitionEntry;
import lombok.ast.While;
import lombok.ast.WildcardKind;

public class SourcePrinter extends ForwardingASTVisitor {
	private final SourceFormatter formatter;
	
	public SourcePrinter(SourceFormatter formatter) {
		this.formatter = formatter;
	}
	
	//Private utility methods
	private void visit(Node node) {
		if (node != null) node.accept(this);
	}
	
	@Override public boolean visitNode(Node node) {
		formatter.buildBlock(node);
		formatter.fail("NOT_IMPLEMENTED: " + node.getClass().getSimpleName());
		formatter.closeBlock();
		return false;
	}
	
	private void append(String text) {
		if (" ".equals(text)) formatter.space();
		else if ("\n".equals(text)) formatter.verticalSpace();
		else formatter.append(text);
	}
	
	private void visitAll(ListAccessor<?, ?> nodes, String separator, String prefix, String suffix) {
		if (nodes.isEmpty()) return;
		append(prefix);
		boolean first = true;
		for (Node n : nodes.getRawContents()) {
			if (!first) {
				append(separator);
			}
			first = false;
			visit(n);
		}
		append(suffix);
	}
	
	private boolean isValidJavaIdentifier(String in) {
		if (in == null || in.length() == 0) return false;
		
		char c = in.charAt(0);
		if (!Character.isJavaIdentifierStart(c)) return false;
		char[] cs = in.toCharArray();
		for (int i = 1; i < cs.length; i++) {
			if (!Character.isJavaIdentifierPart(cs[i])) return false;
		}
		return true;
	}
	
	//Basics
	public boolean visitTypeReference(TypeReference node) {
		WildcardKind kind = node.getWildcard();
		formatter.buildInline(node);
		if (kind == WildcardKind.UNBOUND) {
			formatter.append("?");
			formatter.closeInline();
			return true;
		} else if (kind == WildcardKind.EXTENDS) {
			formatter.append("?");
			formatter.space();
			formatter.keyword("extends");
			formatter.space();
		} else if (kind == WildcardKind.SUPER) {
			formatter.append("?");
			formatter.space();
			formatter.keyword("super");
			formatter.space();
		}
		
		visitAll(node.parts(), ".", "", "");
		
		for (int i = 0 ; i < node.getArrayDimensions(); i++)
			formatter.append("[]");
		
		formatter.closeInline();
		return true;
	}
	
	public boolean visitTypeReferencePart(TypeReferencePart node) {
		formatter.buildInline(node);
		visit(node.getRawIdentifier());
		visit(node.getRawTypeArguments());
		formatter.closeInline();
		return true;
	}
	
	public boolean visitIdentifier(Identifier node) {
		String name = node.getName();
		if (!node.isSyntacticallyValid()) {
			if (name == null || name.isEmpty()) {
				formatter.reportAssertionFailureNext(node, "null or empty identifier that is nevertheless syntactically valid", null);
			} else if (!isValidJavaIdentifier(name)) {
				formatter.reportAssertionFailureNext(node, "identifier name contains characters that aren't legal in an identifier", null);
			}
		}
		
		if (name == null) name = FAIL + "NULL_IDENTIFIER" + FAIL;
		else if (name.isEmpty()) name = FAIL + "EMPTY_IDENTIFIER" + FAIL;
		else if (!isValidJavaIdentifier(name)) name = FAIL + "INVALID_IDENTIFIER: " + name + FAIL;
		
		formatter.buildInline(node);
		formatter.append(name);
		formatter.closeInline();
		return true;
	}
	
	public boolean visitIntegralLiteral(IntegralLiteral node) {
		String raw = node.getRawValue();
		try {
			IntegralLiteral il = new IntegralLiteral();
			if (node.isMarkedAsLong()) il.setLongValue(node.longValue());
			else il.setIntValue(node.intValue());
			raw = il.getRawValue();
		} catch (Exception e) {
			if (node.isSyntacticallyValid()) {
				formatter.reportAssertionFailureNext(node, "correct integral literal nevertheless does not pass getValue->setValue->getRawValue process.", e);
			}
		}
		
		formatter.buildInline(node);
		formatter.append(raw);
		formatter.closeInline();
		return true;
	}
	
	public boolean visitFloatingPointLiteral(FloatingPointLiteral node) {
		String raw = node.getRawValue();
		try {
			FloatingPointLiteral fpl = new FloatingPointLiteral();
			if (node.isMarkedAsFloat()) fpl.setFloatValue(node.floatValue());
			else fpl.setDoubleValue(node.doubleValue());
			raw = fpl.getRawValue();
		} catch (Exception e) {
			if (node.isSyntacticallyValid()) {
				formatter.reportAssertionFailureNext(node, "correct floating point literal nevertheless does not pass getValue->setValue->getRawValue process.", e);
			}
		}
		
		formatter.buildInline(node);
		formatter.append(raw);
		formatter.closeInline();
		return true;
	}
	
	public boolean visitBooleanLiteral(BooleanLiteral node) {
		String raw = node.getRawValue();
		try {
			raw = new BooleanLiteral().setValue(node.getValue()).getRawValue();
		} catch (Exception e) {
			if (node.isSyntacticallyValid()) {
				formatter.reportAssertionFailureNext(node, "correct boolean literal nevertheless does not pass getValue->setValue->getRawValue process.", e);
			}
		}
		
		formatter.buildInline(node);
		formatter.append(raw);
		formatter.closeInline();
		return true;
	}
	
	public boolean visitCharLiteral(CharLiteral node) {
		String raw = node.getRawValue();
		try {
			raw = new CharLiteral().setValue(node.getValue()).getRawValue();
		} catch (Exception e) {
			if (node.isSyntacticallyValid()) {
				formatter.reportAssertionFailureNext(node, "correct char literal nevertheless does not pass getValue->setValue->getRawValue process.", e);
			}
		}
		
		formatter.buildInline(node);
		formatter.append(raw);
		formatter.closeInline();
		return true;
	}
	
	public boolean visitStringLiteral(StringLiteral node) {
		String raw = node.getRawValue();
		try {
			raw = new StringLiteral().setValue(node.getValue()).getRawValue();
		} catch (Exception e) {
			if (node.isSyntacticallyValid()) {
				formatter.reportAssertionFailureNext(node, "correct string literal nevertheless does not pass getValue->setValue->getRawValue process.", e);
			}
		}
		
		formatter.buildInline(node);
		formatter.append(raw);
		formatter.closeInline();
		return true;
	}
	
	public boolean visitNullLiteral(NullLiteral node) {
		formatter.buildInline(node);
		formatter.keyword("null");
		formatter.closeInline();
		return true;
	}

	//Expressions
	public boolean visitBinaryExpression(BinaryExpression node) {
		formatter.buildInline(node);
		boolean parens = node.needsParentheses();
		if (parens) formatter.append("(");
		visit(node.getRawLeft());
		formatter.space();
		try {
			formatter.operator(node.getOperator().getSymbol());
		} catch (Exception e) {
			formatter.operator(node.getRawOperator());
		}
		formatter.space();
		visit(node.getRawRight());
		if (parens) formatter.append(")");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitUnaryExpression(UnaryExpression node) {
		formatter.buildInline(node);
		UnaryOperator op;
		try {
			op = node.getOperator();
		} catch (Exception e) {
			visitNode(node.getOperand());
			formatter.closeInline();
			return true;
		}
		boolean parens = node.needsParentheses();
		if (parens) formatter.append("(");
		if (!op.isPostfix()) formatter.operator(op.getSymbol());
		visitNode(node.getOperand());
		if (op.isPostfix()) formatter.operator(op.getSymbol());
		if (parens) formatter.append(")");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitCast(Cast node) {
		formatter.buildInline(node);
		boolean parens = node.needsParentheses();
		if (parens) formatter.append("(");
		formatter.append("(");
		visitNode(node.getRawTypeReference());
		formatter.append(")");
		formatter.space();
		visitNode(node.getRawOperand());
		if (parens) formatter.append(")");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitInlineIfExpression(InlineIfExpression node) {
		formatter.buildInline(node);
		boolean parens = node.needsParentheses();
		if (parens) formatter.append("(");
		visit(node.getRawCondition());
		formatter.space();
		formatter.operator("?");
		formatter.space();
		visit(node.getRawIfTrue());
		formatter.space();
		formatter.operator(":");
		formatter.space();
		visit(node.getRawIfFalse());
		if (parens) formatter.append(")");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitInstanceOf(InstanceOf node) {
		formatter.buildInline(node);
		boolean parens = node.needsParentheses();
		if (parens) formatter.append("(");
		visit(node.getRawObjectReference());
		formatter.space();
		formatter.keyword("instanceof");
		formatter.space();
		visit(node.getRawTypeReference());
		if (parens) formatter.append(")");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitConstructorInvocation(ConstructorInvocation node) {
		formatter.buildInline(node);
		if (node.getRawQualifier() != null) {
			visit(node.getRawQualifier());
			formatter.append(".");
		}
		formatter.keyword("new");
		formatter.space();
		visit(node.getRawConstructorTypeArguments());
		visit(node.getRawTypeReference());
		formatter.append("(");
		visitAll(node.arguments(), ", ", "", "");
		formatter.append(")");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitMethodInvocation(MethodInvocation node) {
		formatter.buildInline(node);
		if (node.getRawOperand() != null) {
			visit(node.getRawOperand());
			formatter.append(".");
		}
		visit(node.getRawMethodTypeArguments());
		visit(node.getRawName());
		formatter.append("(");
		visitAll(node.arguments(), ", ", "", "");
		formatter.append(")");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitSelect(Select node) {
		formatter.buildInline(node);
		if (node.getRawOperand() != null) {
			visit(node.getRawOperand());
			formatter.append(".");
		}
		visit(node.getRawIdentifier());
		formatter.closeInline();
		return true;
	}
	
	public boolean visitArrayAccess(ArrayAccess node) {
		formatter.buildInline(node);
		visit(node.getRawOperand());
		formatter.append("[");
		visit(node.getRawIndexExpression());
		formatter.append("]");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitArrayCreation(ArrayCreation node) {
		formatter.buildInline(node);
		formatter.keyword("new");
		formatter.space();
		visit(node.getRawComponentTypeReference());
		visitAll(node.dimensions(), "", "", "");
		if (node.getRawInitializer() != null) {
			formatter.space();
			visit(node.getRawInitializer());
		}
		formatter.closeInline();
		return true;
	}
	
	public boolean visitArrayInitializer(ArrayInitializer node) {
		formatter.buildInline(node);
		formatter.append("{");
		visitAll(node.expressions(), ", ", "", "");
		formatter.append("}");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitArrayDimension(ArrayDimension node) {
		formatter.buildInline(node);
		formatter.append("[");
		visit(node.getRawDimension());
		formatter.append("]");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitClassLiteral(ClassLiteral node) {
		formatter.buildInline(node);
		visit(node.getRawTypeReference());
		formatter.append(".");
		formatter.keyword("class");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitSuper(Super node) {
		formatter.buildInline(node);
		if (node.getRawQualifier() != null) {
			visit(node.getRawQualifier());
			formatter.append(".");
		}
		formatter.keyword("super");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitThis(This node) {
		formatter.buildInline(node);
		if (node.getRawQualifier() != null) {
			visit(node.getRawQualifier());
			formatter.append(".");
		}
		formatter.keyword("this");
		formatter.closeInline();
		return true;
	}
	
	//Statements
	public boolean visitExpressionStatement(ExpressionStatement node) {
		formatter.buildBlock(node);
		visit(node.getRawExpression());
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitLabelledStatement(LabelledStatement node) {
		formatter.buildBlock(node);
		if (node.getRawLabel() != null) {
			visit(node.getRawLabel());
			formatter.append(":");
		}
		visit(node.getRawStatement());
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitIf(If node) {
		formatter.buildBlock(node);
		formatter.keyword("if");
		formatter.space();
		formatter.append("(");
		visit(node.getRawCondition());
		formatter.append(")");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawStatement());
		formatter.startSuppressBlock();
		if (node.getRawElseStatement() != null) {
			formatter.space();
			formatter.keyword("else");
			formatter.space();
			formatter.startSuppressBlock();
			visit(node.getRawElseStatement());
			formatter.startSuppressBlock();
		}
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitFor(For node) {
		formatter.buildBlock(node);
		formatter.keyword("for");
		formatter.space();
		formatter.append("(");
		visitAll(node.inits(), ", ", "", "");
		formatter.append(";");
		formatter.space();
		visit(node.getRawCondition());
		formatter.append(";");
		formatter.space();
		visitAll(node.updates(), ", ", "", "");
		formatter.append(")");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawStatement());
		formatter.endSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitForEach(ForEach node) {
		formatter.buildBlock(node);
		formatter.keyword("for");
		formatter.space();
		formatter.append("(");
		visit(node.getRawVariable());
		formatter.space();
		formatter.append(":");
		formatter.space();
		visit(node.getRawIterable());
		formatter.append(")");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawStatement());
		formatter.endSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitTry(Try node) {
		formatter.buildBlock(node);
		formatter.keyword("try");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.endSuppressBlock();
		visitAll(node.catches(), " ", " ", "");
		if (node.getRawFinally() != null) {
			formatter.space();
			formatter.keyword("finally");
			formatter.space();
			formatter.startSuppressBlock();
			visit(node.getRawFinally());
			formatter.endSuppressBlock();
		}
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitCatch(Catch node) {
		formatter.buildInline(node);
		formatter.keyword("catch");
		formatter.space();
		formatter.append("(");
		visit(node.getRawExceptionDeclaration());
		formatter.append(")");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.endSuppressBlock();
		formatter.closeInline();
		return true;
	}
	
	public boolean visitWhile(While node) {
		formatter.buildBlock(node);
		formatter.keyword("while");
		formatter.space();
		formatter.append("(");
		visit(node.getRawCondition());
		formatter.append(")");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawStatement());
		formatter.endSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitDoWhile(DoWhile node) {
		formatter.buildBlock(node);
		formatter.keyword("do");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawStatement());
		formatter.endSuppressBlock();
		formatter.space();
		formatter.keyword("while");
		formatter.space();
		formatter.append("(");
		visit(node.getRawCondition());
		formatter.append(")");
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitSynchronized(Synchronized node) {
		formatter.buildBlock(node);
		formatter.keyword("synchronized");
		formatter.space();
		formatter.append("(");
		visit(node.getRawLock());
		formatter.append(")");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getBody());
		formatter.endSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitBlock(Block node) {
		formatter.buildBlock(node);
		formatter.append("{");
		formatter.buildBlock(null);
		visitAll(node.contents(), "", "", "");
		formatter.closeBlock();
		formatter.append("}");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitAssert(Assert node) {
		formatter.buildBlock(node);
		formatter.keyword("assert");
		formatter.space();
		visit(node.getRawAssertion());
		if (node.getRawMessage() != null) {
			formatter.space();
			formatter.append(":");
			formatter.space();
			visit(node.getRawMessage());
		}
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitEmptyStatement(EmptyStatement node) {
		formatter.buildBlock(node);
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitSwitch(Switch node) {
		formatter.buildBlock(node);
		formatter.keyword("switch");
		formatter.space();
		formatter.append("(");
		visit(node.getRawCondition());
		formatter.append(")");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.endSuppressBlock();
		return true;
	}
	
	public boolean visitCase(Case node) {
		formatter.buildBlock(node);
		formatter.keyword("case");
		formatter.space();
		visit(node.getRawCondition());
		formatter.append(":");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitDefault(Default node) {
		formatter.buildBlock(node);
		formatter.keyword("default");
		formatter.append(":");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitBreak(Break node) {
		formatter.buildBlock(node);
		formatter.keyword("break");
		if (node.getRawLabel() != null) {
			formatter.space();
			visit(node.getRawLabel());
		}
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitContinue(Continue node) {
		formatter.buildBlock(node);
		formatter.keyword("continue");
		if (node.getRawLabel() != null) {
			formatter.space();
			visit(node.getRawLabel());
		}
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitReturn(Return node) {
		formatter.buildBlock(node);
		formatter.keyword("return");
		if (node.getRawValue() != null) {
			formatter.space();
			visit(node.getRawValue());
		}
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitThrow(Throw node) {
		formatter.buildBlock(node);
		formatter.keyword("throw");
		formatter.space();
		node.getRawThrowable();
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	//Structural
	public boolean visitVariableDeclaration(VariableDeclaration node) {
		formatter.buildBlock(node);
		visit(node.getRawDefinition());
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	public boolean visitVariableDefinition(VariableDefinition node) {
		formatter.buildInline(node);
		if (node.getRawModifiers() != null) {
			visit(node.getRawModifiers());
			if (node.getRawModifiers() instanceof Modifiers && !((Modifiers)node.getRawModifiers()).keywords().isEmpty()) {
				formatter.space();
			}
		}
		visit(node.getRawTypeReference());
		formatter.space();
		visitAll(node.variables(), ", ", "", "");
		formatter.closeInline();
		
		return true;
	}
	
	public boolean visitVariableDefinitionEntry(VariableDefinitionEntry node) {
		formatter.buildInline(node);
		visit(node.getRawName());
		for (int i = 0; i < node.getDimensions(); i++)
			formatter.append("[]");
		if (node.getRawInitializer() != null) {
			formatter.append(" = ");
			visit(node.getRawInitializer());
		}
		formatter.closeInline();
		
		return true;
	}
	
	public boolean visitTypeArguments(TypeArguments node) {
		formatter.buildInline(node);
		visitAll(node.generics(), ", ", "<", ">");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitTypeVariable(TypeVariable node) {
		formatter.buildInline(node);
		visit(node.getRawName());
		if (!node.extending().isEmpty()) {
			formatter.keyword("extends");
			visitAll(node.extending(), " & ", " ", "");
		}
		formatter.closeInline();
		return true;
	}
	
	public boolean visitKeywordModifier(KeywordModifier node) {
		formatter.buildInline(node);
		if (node.getName() == null || node.getName().isEmpty()) formatter.fail("MISSING_MODIFIER");
		else
			formatter.keyword(node.getName());
		formatter.closeInline();
		return true;
	}
	
	public boolean visitModifiers(Modifiers node) {
		formatter.buildInline(node);
		visitAll(node.annotations(), "", "", "");
		visitAll(node.keywords(), " ", "", "");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitAnnotation(Annotation node) {
		formatter.buildInline(node);
		formatter.append("@");
		visit(node.getRawAnnotationTypeReference());
		visitAll(node.elements(), ", ", "(", ")");
		formatter.closeInline();
		return true;
	}
	
	public boolean visitAnnotationElement(AnnotationElement node) {
		formatter.buildInline(node);
		if (node.getRawName() != null) {
			visit(node.getRawName());
			formatter.space();
			formatter.append("=");
			formatter.space();
		}
		visit(node.getValue());
		formatter.closeInline();
		return true;
	}
	
	public boolean visitTypeBody(TypeBody node) {
		formatter.buildBlock(node);
		formatter.append("{");
		formatter.buildBlock(null);
		if (node.getParent() instanceof EnumDeclaration) {
			formatter.buildBlock(null);
			visitAll(((EnumDeclaration)node.getParent()).constants(), ", ", "", "");
			if (!node.members().isEmpty()) formatter.append(";");
			formatter.closeBlock();
		}
		visitAll(node.members(), "\n", "", "");
		formatter.closeBlock();
		formatter.append("}");
		formatter.closeBlock();
		return true;
	}
	
	//Class Bodies
	public boolean visitMethodDeclaration(MethodDeclaration node) {
		formatter.buildBlock(node);
		if (node.getRawModifiers() != null) {
			visit(node.getRawModifiers());
			if (node.getRawModifiers() instanceof Modifiers && !((Modifiers)node.getRawModifiers()).keywords().isEmpty()) {
				formatter.space();
			}
		}
		visitAll(node.typeVariables(), ", ", "<", ">");
		if (!node.typeVariables().isEmpty()) formatter.space();
		visit(node.getRawReturnTypeReference());
		formatter.space();
		visit(node.getRawMethodName());
		formatter.append("(");
		visitAll(node.parameters(), ", ", "", "");
		formatter.append(")");
		formatter.space();
		if (!node.thrownTypeReferences().isEmpty()) {
			formatter.keyword("throws");
			visitAll(node.thrownTypeReferences(), ", ", " ", " ");
		}
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		if (node.getRawBody() == null) {
			formatter.append(";");
		}
		formatter.endSuppressBlock();
		formatter.closeBlock();
		
		return true;
	}
	
	public boolean visitConstructorDeclaration(ConstructorDeclaration node) {
		formatter.buildBlock(node);
		if (node.getRawModifiers() != null) {
			visit(node.getRawModifiers());
			if (node.getRawModifiers() instanceof Modifiers && !((Modifiers)node.getRawModifiers()).keywords().isEmpty()) {
				formatter.space();
			}
		}
		visitAll(node.typeVariables(), ", ", "<", ">");
		if (!node.typeVariables().isEmpty()) formatter.space();
		visit(node.getRawTypeName());
		formatter.append("(");
		visitAll(node.parameters(), ", ", "", "");
		formatter.append(")");
		formatter.space();
		if (!node.thrownTypeReferences().isEmpty()) {
			formatter.keyword("throws");
			visitAll(node.thrownTypeReferences(), ", ", " ", " ");
		}
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		if (node.getRawBody() == null) {
			formatter.append(";");
		}
		formatter.endSuppressBlock();
		formatter.closeBlock();
		
		return true;
	}
	
	public boolean visitSuperConstructorInvocation(SuperConstructorInvocation node) {
		formatter.buildBlock(node);
		if (node.getRawQualifier() != null) {
			visit(node.getRawQualifier());
			formatter.append(".");
		}
		visit(node.getRawConstructorTypeArguments());
		formatter.keyword("super");
		formatter.append("(");
		visitAll(node.arguments(), ", ", "", "");
		formatter.append(")");
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitAlternateConstructorInvocation(AlternateConstructorInvocation node) {
		formatter.buildBlock(node);
		visit(node.getRawConstructorTypeArguments());
		formatter.keyword("this");
		formatter.append("(");
		visitAll(node.arguments(), ", ", "", "");
		formatter.append(")");
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitInstanceInitializer(InstanceInitializer node) {
		formatter.buildBlock(node);
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.startSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitStaticInitializer(StaticInitializer node) {
		formatter.buildBlock(node);
		formatter.keyword("static");
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.startSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitClassDeclaration(ClassDeclaration node) {
		formatter.buildBlock(node);
		if (node.getRawModifiers() != null) {
			visit(node.getRawModifiers());
			if (node.getRawModifiers() instanceof Modifiers && !((Modifiers)node.getRawModifiers()).keywords().isEmpty()) {
				formatter.space();
			}
		}
		formatter.keyword("class");
		formatter.space();
		visit(node.getRawName());
		visitAll(node.typeVariables(), ", ", "<", ">");
		formatter.space();
		if (node.getRawExtending() != null) {
			formatter.keyword("extends");
			formatter.space();
			visit(node.getRawExtending());
			formatter.space();
		}
		if (!node.implementing().isEmpty()) {
			formatter.keyword("implements");
			visitAll(node.implementing(), ", ", " ", " ");
		}
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.endSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitInterfaceDeclaration(InterfaceDeclaration node) {
		formatter.buildBlock(node);
		if (node.getRawModifiers() != null) {
			visit(node.getRawModifiers());
			if (node.getRawModifiers() instanceof Modifiers && !((Modifiers)node.getRawModifiers()).keywords().isEmpty()) {
				formatter.space();
			}
		}
		formatter.keyword("interface");
		formatter.space();
		visit(node.getRawName());
		visitAll(node.typeVariables(), ", ", "<", ">");
		formatter.space();
		if (!node.extending().isEmpty()) {
			formatter.keyword("extends");
			visitAll(node.extending(), ", ", " ", " ");
		}
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.endSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitEnumDeclaration(EnumDeclaration node) {
		formatter.buildBlock(node);
		if (node.getRawModifiers() != null) {
			visit(node.getRawModifiers());
			if (node.getRawModifiers() instanceof Modifiers && !((Modifiers)node.getRawModifiers()).keywords().isEmpty()) {
				formatter.space();
			}
		}
		formatter.keyword("enum");
		formatter.space();
		visit(node.getRawName());
		formatter.space();
		if (!node.implementing().isEmpty()) {
			formatter.keyword("implements");
			visitAll(node.implementing(), ", ", " ", " ");
		}
		
		//logic of printing enum constants is in visitTypeBody
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.endSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitEnumConstant(EnumConstant node) {
		formatter.buildInline(node);
		visitAll(node.annotations(), "", "", "");
		visit(node.getRawName());
		visitAll(node.arguments(), ", ", "(", ")");
		if (node.getRawBody() != null) {
			formatter.space();
			visit(node.getRawBody());
		}
		formatter.closeInline();
		return true;
	}
	
	public boolean visitAnnotationDeclaration(AnnotationDeclaration node) {
		formatter.buildBlock(node);
		if (node.getRawModifiers() != null) {
			visit(node.getRawModifiers());
			if (node.getRawModifiers() instanceof Modifiers && !((Modifiers)node.getRawModifiers()).keywords().isEmpty()) {
				formatter.space();
			}
		}
		formatter.append("@");
		formatter.keyword("interface");
		formatter.space();
		visit(node.getRawName());
		formatter.space();
		formatter.startSuppressBlock();
		visit(node.getRawBody());
		formatter.endSuppressBlock();
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitAnnotationMethodDeclaration(AnnotationMethodDeclaration node) {
		formatter.buildBlock(node);
		if (node.getRawModifiers() != null) {
			visit(node.getRawModifiers());
			if (node.getRawModifiers() instanceof Modifiers && !((Modifiers)node.getRawModifiers()).keywords().isEmpty()) {
				formatter.space();
			}
		}
		visit(node.getRawReturnTypeReference());
		formatter.space();
		visit(node.getRawMethodName());
		formatter.append("(");
		formatter.append(")");
		if (node.getRawDefaultValue() != null) {
			formatter.space();
			formatter.keyword("default");
			formatter.space();
			visit(node.getRawDefaultValue());
		}
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitCompilationUnit(CompilationUnit node) {
		formatter.buildBlock(node);
		if (node.getRawPackageDeclaration() != null) {
			visit(node.getRawPackageDeclaration());
			formatter.verticalSpace();
		}
		visitAll(node.importDeclarations(), "", "", "\n");
		visitAll(node.typeDeclarations(), "\n", "", "");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitPackageDeclaration(PackageDeclaration node) {
		formatter.buildBlock(node);
		visitAll(node.annotations(), "", "", "");
		formatter.keyword("package");
		formatter.space();
		visitAll(node.parts(), ".", "", "");
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	public boolean visitImportDeclaration(ImportDeclaration node) {
		formatter.buildBlock(node);
		formatter.keyword("import");
		formatter.space();
		if (node.isStaticImport()) {
			formatter.keyword("static");
			formatter.space();
		}
		visitAll(node.parts(), ".", "", "");
		if (node.isStarImport()) {
			formatter.append(".*");
		}
		formatter.append(";");
		formatter.closeBlock();
		return true;
	}
	
	//Various
	public boolean visitParseArtefact(Node node) {
		formatter.buildInline(node);
		formatter.fail("ARTEFACT: " + node.getClass().getSimpleName());
		formatter.closeInline();
		return true;
	}
	
	public boolean visitComment(Comment node) {
		formatter.buildBlock(node);
		formatter.append(node.isBlockComment() ? "/*" : "//");
		if (node.getContent() == null) formatter.fail("MISSING_COMMENT");
		else
			formatter.append(node.getContent());
		if (node.isBlockComment()) formatter.append("*/");
		formatter.closeBlock();
		return true;
	}
}
