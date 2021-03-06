package org.intellij.plugins.markdown.structureView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.LocationPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Consumer;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets.*;

class MarkdownStructureElement extends PsiTreeElementBase<PsiElement> implements SortableTreeElement, LocationPresentation,
                                                                                 Queryable {

  static final TokenSet PRESENTABLE_TYPES = HEADERS;

  static final TokenSet TRANSPARENT_CONTAINERS = TokenSet.create(MARKDOWN_FILE, UNORDERED_LIST, ORDERED_LIST, LIST_ITEM, BLOCK_QUOTE);

  private static final ItemPresentation DUMMY_PRESENTATION = new MarkdownBasePresentation() {

    @Nullable
    @Override
    public String getPresentableText() {
      return null;
    }

    @Nullable
    @Override
    public String getLocationString() {
      return null;
    }
  };

  private static final List<TokenSet> HEADER_ORDER = Arrays.asList(
    TokenSet.create(MarkdownElementTypes.MARKDOWN_FILE_ELEMENT_TYPE),
    HEADER_LEVEL_1_SET,
    HEADER_LEVEL_2_SET,
    HEADER_LEVEL_3_SET,
    HEADER_LEVEL_4_SET,
    HEADER_LEVEL_5_SET,
    HEADER_LEVEL_6_SET);


  MarkdownStructureElement(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  public boolean canNavigate() {
    return getElement() instanceof NavigationItem && ((NavigationItem)getElement()).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getElement() instanceof NavigationItem && ((NavigationItem)getElement()).canNavigateToSource();
  }


  @Override
  public void navigate(boolean requestFocus) {
    if (getElement() instanceof NavigationItem) {
      ((NavigationItem)getElement()).navigate(requestFocus);
    }
  }

  @NotNull
  @Override
  public String getAlphaSortKey() {
    return StringUtil.notNullize(getElement() instanceof NavigationItem ?
                                 ((NavigationItem)getElement()).getName() : null);
  }

  @Override
  public boolean isSearchInLocationString() {
    return true;
  }

  @Nullable
  @Override
  public String getPresentableText() {
    final PsiElement tag = getElement();
    if (tag == null) {
      return IdeBundle.message("node.structureview.invalid");
    }
    return getPresentation().getPresentableText();
  }

  @Override
  public String getLocationString() {
    return getPresentation().getLocationString();
  }

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    if (getElement() instanceof PsiFileImpl) {
      ItemPresentation filePresent = ((PsiFileImpl)getElement()).getPresentation();
      return filePresent != null ? filePresent : DUMMY_PRESENTATION;
    }

    if (getElement() instanceof NavigationItem) {
      final ItemPresentation itemPresent = ((NavigationItem)getElement()).getPresentation();
      if (itemPresent != null) {
        return itemPresent;
      }
    }

    return DUMMY_PRESENTATION;
  }


  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final List<StructureViewTreeElement> childrenElements = new ArrayList<>();

    final PsiElement myElement = getElement();
    if (myElement == null) return childrenElements;

    final PsiElement structureContainer = myElement instanceof MarkdownFile ? myElement.getFirstChild()
                                                                            : getParentOfType(myElement, TRANSPARENT_CONTAINERS);
    if (structureContainer == null) {
      return Collections.emptyList();
    }

    final MarkdownPsiElement currentHeader = myElement instanceof MarkdownHeaderImpl ? ((MarkdownHeaderImpl)myElement) : null;
    processContainer(structureContainer, currentHeader, currentHeader,
                     element -> childrenElements.add(new MarkdownStructureElement(element)));

    return childrenElements;
  }

  private static void processContainer(@NotNull PsiElement container,
                                       @Nullable PsiElement sameLevelRestriction,
                                       @Nullable MarkdownPsiElement from,
                                       @NotNull Consumer<? super PsiElement> resultConsumer) {
    PsiElement nextSibling = from == null ? container.getFirstChild() : from.getNextSibling();
    PsiElement maxContentLevel = null;
    while (nextSibling != null) {
      if (TRANSPARENT_CONTAINERS.contains(PsiUtilCore.getElementType(nextSibling)) && maxContentLevel == null) {
        processContainer(nextSibling, null, null, resultConsumer);
      }
      else if (nextSibling instanceof MarkdownHeaderImpl) {
        if (sameLevelRestriction != null && isSameLevelOrHigher(nextSibling, sameLevelRestriction)) {
          break;
        }

        if (maxContentLevel == null || isSameLevelOrHigher(nextSibling, maxContentLevel)) {
          maxContentLevel = nextSibling;

          final IElementType type = nextSibling.getNode().getElementType();
          if (PRESENTABLE_TYPES.contains(type)) {
            resultConsumer.consume(nextSibling);
          }
        }
      }

      nextSibling = nextSibling.getNextSibling();
    }
  }

  private static boolean isSameLevelOrHigher(@NotNull PsiElement psiA, @NotNull PsiElement psiB) {
    IElementType typeA = psiA.getNode().getElementType();
    IElementType typeB = psiB.getNode().getElementType();

    return headerLevel(typeA) <= headerLevel(typeB);
  }


  private static int headerLevel(@NotNull IElementType curLevelType) {
    for (int i = 0; i < HEADER_ORDER.size(); i++) {
      if (HEADER_ORDER.get(i).contains(curLevelType)) {
        return i;
      }
    }

    // not a header so return lowest level
    return Integer.MAX_VALUE;
  }

  @Nullable
  private static PsiElement getParentOfType(@NotNull PsiElement myElement, @NotNull TokenSet types) {
    final ASTNode parentNode = TreeUtil.findParent(myElement.getNode(), types);
    return parentNode == null ? null : parentNode.getPsi();
  }

  @NotNull
  @Override
  public String getLocationPrefix() {
    return " ";
  }

  @NotNull
  @Override
  public String getLocationSuffix() {
    return "";
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    info.put("text", getPresentableText());
    if (!(getElement() instanceof PsiFileImpl)) {
      info.put("location", getLocationString());
    }
  }
}
