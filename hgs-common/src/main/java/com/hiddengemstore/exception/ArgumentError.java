package com.hiddengemstore.exception;

import lombok.Data;

/**
 * 参数错误
 * @author : ZhaoJH
 **/
@Data
public class ArgumentError {
	
	private String argumentName;
	
	private String message;
}
