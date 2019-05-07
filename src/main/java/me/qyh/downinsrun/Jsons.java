package me.qyh.downinsrun;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Jsons {
	private static final String SPLIT_STR = "->";
	private static final String[] EMPTY_ARRAY = new String[0];

	/**
	 * 读取内容(必须为json字符串)。通过表达式获取指定内容
	 *
	 * @param json
	 * @return
	 */
	public static ExpressionExecutors readJsonForExecutors(String json) {
		try {
			JsonParser jp = new JsonParser();
			JsonElement je = jp.parse(json);
			return new ExpressionExecutors(toJsonArray(je));
		} catch (Exception e) {
			return new ExpressionExecutors(new JsonArray());
		}
	}

	public static final class ExpressionExecutor {

		private static final Expression NULL_EXPRESSION = new NullExpression();

		private final JsonElement ele;

		private final Throwable ex;

		private ExpressionExecutor(JsonElement ele) {
			this(ele, null);
		}

		private ExpressionExecutor(JsonElement ele, Throwable ex) {
			this.ele = ele;
			this.ex = ex;
		}

		public JsonElement toJsonElement() {
			return ele;
		}

		/**
		 * 执行一个表达式
		 * 
		 * <pre>
		 * data:{
		 * 	data:{
		 * 		success:true
		 * 	}
		 * }
		 * executeForExecutor(data).executeForExecutor(data).executeForExecutor(success).get() ==> 'true'
		 * </pre>
		 * 
		 * @param expression
		 * @return
		 */
		public ExpressionExecutor executeForExecutor(String expression) {
			return new ExpressionExecutor(doExecute(expression));
		}

		/**
		 * 执行一个表达式
		 * 
		 * <pre>
		 * data:{
		 * 	data:{
		 * 		success:true
		 * 	}
		 * }
		 * executeForExecutors(data).getExpressionExecutor(0).executeForExecutor(data).executeForExecutor(success).get() ==> 'true'
		 * 
		 * 
		 * data:{
		 * 	datas:[
		 * 	{
		 * 		success:true
		 * 	},{
		 * 		success:false
		 * 	}	
		 * 	]
		 * }
		 * ExpressionExecutors executors = executeForExecutors(data-&gt;datas);
		 * for(int i=0;i&lt;executors.size();i++){
		 * 	ExpressionExecutor executor = executors.get(i);
		 *  executor.execute(success) ==&gt; true,false
		 * }
		 * </pre>
		 * 
		 * @param expression
		 * @return
		 */
		public ExpressionExecutors executeForExecutors(String expression) {
			JsonElement ele = doExecute(expression);
			if (ele.isJsonNull()) {
				return new ExpressionExecutors(new JsonArray(0));
			}
			return new ExpressionExecutors(toJsonArray(ele));
		}

		/**
		 * 执行表达式，并返回结果
		 * 
		 * <pre>
		 * data:{
		 * 	data:{
		 * 		success:true
		 * 	}
		 * }
		 * execute(data) ==> data:{success:true}
		 * execute(data-&gt;data-&gt;success) ==> 'true'
		 * </pre>
		 * 
		 * @param expression
		 * @return
		 */
		public Optional<String> execute(String expression) {
			if (expression == null || expression.trim().isEmpty()) {
				return isNull() ? Optional.empty()
						: Optional.of(ele.isJsonPrimitive() ? ele.getAsString() : ele.toString());
			}
			JsonElement executed = doExecute(expression);
			return executed == JsonNull.INSTANCE ? Optional.empty()
					: executed.isJsonPrimitive() ? Optional.of(executed.getAsString())
							: Optional.of(executed.toString());
		}

		public Optional<String> get() {
			return execute(null);
		}

		private JsonElement doExecute(String expression) {
			if (isNull()) {
				return JsonNull.INSTANCE;
			}
			List<Expression> expressionList = parseExpressions(expression);
			JsonElement executed = null;
			for (Expression exp : expressionList) {
				if (exp == NULL_EXPRESSION) {
					return JsonNull.INSTANCE;
				}
				if (executed == null) {
					executed = exp.get(ele);
				} else {
					executed = exp.get(executed);
				}
			}
			return executed;
		}

		/**
		 * 结果是否为空
		 * 
		 * @return
		 */
		public boolean isNull() {
			return ele == JsonNull.INSTANCE || (ele.isJsonArray() && ele.getAsJsonArray().size() == 0);
		}

		public Throwable getEx() {
			return ex;
		}

		private static List<Expression> parseExpressions(String expression) {
			expression = expression.replaceAll("\\s+", "");
			if (expression.isEmpty()) {
				return Arrays.asList(NULL_EXPRESSION);
			}
			if (expression.contains(SPLIT_STR)) {
				// multi expressions
				List<Expression> expressionList = new ArrayList<>();
				for (String _expression : expression.split(SPLIT_STR)) {
					_expression = _expression.replaceAll("\\s+", "");
					if (_expression.isEmpty()) {
						return Arrays.asList(NULL_EXPRESSION);
					}
					Expression parsed = parseExpression(_expression);
					if (parsed == NULL_EXPRESSION) {
						return Arrays.asList(NULL_EXPRESSION);
					}
					expressionList.add(parsed);
				}
				return expressionList;
			}
			return Arrays.asList(parseExpression(expression));
		}

		private static Expression parseExpression(String expression) {
			String indexStr = substringBetween(expression, "[", "]");
			if (indexStr != null) {
				try {
					int index = Integer.parseInt(indexStr);
					String _expression = expression.substring(0, expression.indexOf('[')).trim();
					if (!_expression.isEmpty()) {
						return new ArrayExpression(_expression, index);
					}
				} catch (NumberFormatException e) {
				}
			} else {
				return new Expression(expression);
			}
			return NULL_EXPRESSION;
		}

		private static class Expression {
			protected final String expression;

			public Expression(String expression) {
				super();
				this.expression = expression;
			}

			JsonElement get(JsonElement ele) {
				if (ele.isJsonObject()) {
					JsonObject jo = ele.getAsJsonObject();
					if (jo.has(expression)) {
						return jo.get(expression);
					}
				}
				return JsonNull.INSTANCE;
			}
		}

		private static class NullExpression extends Expression {
			public NullExpression() {
				super("");
			}

			JsonElement get(JsonElement ele) {
				return JsonNull.INSTANCE;
			}
		}

		private static class ArrayExpression extends Expression {
			private final int index;

			public ArrayExpression(String expression, int index) {
				super(expression);
				this.index = index;
			}

			@Override
			JsonElement get(JsonElement ele) {
				if (ele.isJsonObject()) {
					JsonObject jo = ele.getAsJsonObject();
					if (jo.has(expression)) {
						JsonElement expressionEle = jo.get(expression);
						if (expressionEle.isJsonArray()) {
							JsonArray array = expressionEle.getAsJsonArray();
							if (index >= 0 && index <= array.size() - 1) {
								return array.get(index);
							}
						}
					}
				}
				return JsonNull.INSTANCE;
			}
		}

		@Override
		public String toString() {
			return ele == JsonNull.INSTANCE ? null : ele.isJsonPrimitive() ? ele.getAsString() : ele.toString();
		}
	}

	public static final class ExpressionExecutors implements Iterable<ExpressionExecutor> {
		private final JsonArray array;
		private final Throwable ex;

		public ExpressionExecutors(JsonArray array, Throwable ex) {
			super();
			this.array = array;
			this.ex = ex;
		}

		public boolean isNull() {
			return array.size() == 0 || (size() == 1 && array.get(0).isJsonNull());
		}

		public ExpressionExecutors(JsonArray array) {
			this(array, null);
		}

		public int size() {
			return array.size();
		}

		public ExpressionExecutor getExpressionExecutor(int index) {
			return new ExpressionExecutor(array.get(index));
		}

		public Throwable getEx() {
			return ex;
		}

		public JsonArray toJsonArray() {
			if (isNull()) {
				return new JsonArray();
			}
			return array;
		}

		@Override
		public Iterator<ExpressionExecutor> iterator() {
			final Iterator<JsonElement> it = array.iterator();
			return new Iterator<Jsons.ExpressionExecutor>() {

				@Override
				public ExpressionExecutor next() {
					return new ExpressionExecutor(it.next());
				}

				@Override
				public boolean hasNext() {
					return it.hasNext();
				}
			};
		}
	}

	public static String substringBetween(final String str, final String open, final String close) {
		if (str == null || open == null || close == null) {
			return null;
		}
		final int start = str.indexOf(open);
		if (start != -1) {
			final int end = str.indexOf(close, start + open.length());
			if (end != -1) {
				return str.substring(start + open.length(), end);
			}
		}
		return null;
	}

	public static String[] substringsBetween(final String str, final String open, final String close) {
		if (str == null || open == null || open.trim().isEmpty() || close == null || close.trim().isEmpty()) {
			return EMPTY_ARRAY;
		}
		final int strLen = str.length();
		if (strLen == 0) {
			return EMPTY_ARRAY;
		}
		final int closeLen = close.length();
		final int openLen = open.length();
		final List<String> list = new ArrayList<>();
		int pos = 0;
		while (pos < strLen - closeLen) {
			int start = str.indexOf(open, pos);
			if (start < 0) {
				break;
			}
			start += openLen;
			final int end = str.indexOf(close, start);
			if (end < 0) {
				break;
			}
			list.add(str.substring(start, end));
			pos = end + closeLen;
		}
		if (list.isEmpty()) {
			return EMPTY_ARRAY;
		}
		return list.toArray(new String[list.size()]);
	}

	public static ExpressionExecutor readJson(String json) {
		JsonElement je;
		try {
			JsonParser jp = new JsonParser();
			je = jp.parse(json);
		} catch (Exception e) {
			je = JsonNull.INSTANCE;
		}

		return new ExpressionExecutor(je);
	}

	private static JsonArray toJsonArray(JsonElement ele) {
		if (ele.isJsonArray()) {
			return ele.getAsJsonArray();
		}
		JsonArray array = new JsonArray(1);
		array.add(ele);
		return array;
	}

	public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public static <T> List<T> readList(Class<T[]> clazz, String json) {
		final T[] jsonToObject = gson.fromJson(json, clazz);
		return new ArrayList<>(Arrays.asList(jsonToObject));
	}
}
