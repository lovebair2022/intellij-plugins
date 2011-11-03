package com.intellij.flex.uiDesigner.libraries {
import com.intellij.flex.uiDesigner.io.AmfUtil;

import flash.system.ApplicationDomain;
import flash.utils.IDataInput;

public class LibrarySet {
  /**
   * Application Domain contains all class definitions
   * If ApplicationDomainCreationPolicy.MULTIPLE, then applicationDomain equals application domain of last library in set
   */
  public var applicationDomain:ApplicationDomain;

  private static const libraries:Vector.<Library> = new Vector.<Library>(16);

  public function LibrarySet(id:int, parent:LibrarySet) {
    _id = id;
    _parent = parent;
  }

  internal var usageCounter:int;

  public function get isLoaded():Boolean {
    return applicationDomain != null;
  }

  public function registerUsage():void {
    usageCounter++;
    if (parent != null) {
      parent.registerUsage();
    }
  }

  private var _loadSize:int;
  public function get loadSize():int {
    return _loadSize;
  }

  private var _id:int;
  public function get id():int {
    return _id;
  }

  private var _parent:LibrarySet;
  public function get parent():LibrarySet {
    return _parent;
  }

  private var _applicationDomainCreationPolicy:ApplicationDomainCreationPolicy;
  public function get applicationDomainCreationPolicy():ApplicationDomainCreationPolicy {
    return _applicationDomainCreationPolicy;
  }

  private var _items:Vector.<LibrarySetItem>;
  public function get items():Vector.<LibrarySetItem> {
    return _items;
  }

  public function readExternal(input:IDataInput):void {
    _applicationDomainCreationPolicy = ApplicationDomainCreationPolicy.enumSet[input.readByte()];
    var n:int = input.readUnsignedByte();
    _loadSize = n;
    _items = new Vector.<LibrarySetItem>(n, true);
    for (var i:int = 0; i < n; i++) {
      const flags:int = input.readByte();
      var libraryId:int = AmfUtil.readUInt29(input);
      var library:Library;
      if ((flags & 2) != 0) {
        library = libraries[libraryId];
      }
      else {
        library = new Library();
        if (libraryId >= libraries.length) {
          libraries.length = Math.max(libraries.length, libraryId) + 8;
        }
        libraries[libraryId] = library;
        library.readExternal(input);
      }

      var parents:Vector.<LibrarySetItem> = readParents(input);
      var item:LibrarySetFileItem = new LibrarySetFileItem(library, parents, (flags & 1) != 0);
      if (parents != null) {
        for each (var parent:LibrarySetItem in parents) {
          parent.addSuccessor(item);
        }
      }

      _items[i] = item;
    }
  }

  private function readParents(input:IDataInput):Vector.<LibrarySetItem> {
    var n:int = input.readUnsignedByte();
    if (n == 0) {
      return null;
    }

    var parents:Vector.<LibrarySetItem> = new Vector.<LibrarySetItem>(n, true);
    for (var i:int = 0; i < n; i++) {
      parents[i] = _items[input.readUnsignedByte()];
    }
    return parents;
  }
}
}