package com.voyage.app.token;

import com.voyage.app.exception.UnauthorizedException;

public class RefreshTokenException extends UnauthorizedException {

  public RefreshTokenException(String message) {
    super(message);
  }
}
