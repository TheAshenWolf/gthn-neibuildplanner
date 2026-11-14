package neibuildplanner.client.bookmark;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.chain.RecipeChainMath;

/**
 * What a bookmark group asks for, before anything you own is taken into account.
 * <ul>
 * <li>Plain group: every bookmarked item with its amount.</li>
 * <li>Crafting-chain group: the raw ingredients at the bottom of the chain. Intermediate products that another recipe
 * in the group makes are not raw ingredients.</li>
 * </ul>
 */
public final class GroupRequirements {

    private GroupRequirements() {}

    public static ItemStackAmount of(RecipeChainMath math) {
        final ItemStackAmount required = new ItemStackAmount();

        if (math.outputRecipes.isEmpty()) {
            for (BookmarkItem item : math.initialItems) {
                addItem(required, item, item.getAmount());
            }

            return required;
        }

        math.refresh();

        // Same rule NEI uses in RecipeChainMath.getMissedItems for ingredients: an ingredient with a producer recipe
        // in the chain is covered by that recipe, everything else is a raw ingredient.
        for (BookmarkItem item : math.recipeIngredients) {
            if (math.requiredAmount.containsKey(math.preferredItems.get(item))) {
                continue;
            }

            addItem(required, item, math.requiredAmount.getOrDefault(item, item.getAmount()));
        }

        return required;
    }

    private static void addItem(ItemStackAmount required, BookmarkItem item, long amount) {
        if (amount <= 0) {
            return;
        }

        // Fluids are bookmarked in mB but pulled and crafted as containers; getStackSize converts.
        required.add(item.itemStack, item.getStackSize(amount));
    }
}
