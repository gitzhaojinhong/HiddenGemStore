package com.hiddengemstore.exception;


import com.hiddengemstore.enums.BaseCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常
 * @author : ZhaoJH
 **/
@EqualsAndHashCode(callSuper = true)
@Data
public class HGSFrameException extends BaseException {
	
	private Integer code;
	
	private String message;

	public HGSFrameException() {
		super();
	}

	public HGSFrameException(String message) {
		super(message);
	}
	
	public HGSFrameException(Integer code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}
	
	public HGSFrameException(BaseCode baseCode) {
		super(baseCode.getMsg());
		this.code = baseCode.getCode();
		this.message = baseCode.getMsg();
	}

	public HGSFrameException(Throwable cause) {
		super(cause);
	}

	public HGSFrameException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}
}
