package io.github.adoct.parser.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use= JsonTypeInfo.Id.CLASS)
public interface LinksValue {

  /**
   * Человекочитаемая подпись ссылки — используется, когда ссылку не удалось зарезолвить (иначе в
   * документ попадал {@code toString()} записи вида {@code LinksPage[title=…, space=…]}).
   */
  String label();
}
