package fr.jayasoft.ivy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Inner helper class for sorting ModuleDescriptors.
 * @author baumkar (for most of the code)
 * @author xavier hanin (for the sorting of nodes based upon sort of modules)
 *
 */
class ModuleDescriptorSorter {
    public static List sortNodes(Collection nodes) {
        /* here we want to use the sort algorithm which work on module descriptors :
         * so we first put dependencies on a map from descriptors to dependency, then we 
         * sort the keySet (i.e. a collection of descriptors), then we replace
         * in the sorted list each descriptor by the corresponding dependency
         */
        
        Map dependenciesMap = new LinkedHashMap();
        List nulls = new ArrayList();
        for (Iterator iter = nodes.iterator(); iter.hasNext();) {
            IvyNode node = (IvyNode)iter.next();
            if (node.getDescriptor() == null) {
                nulls.add(node);
            } else {
                List n = (List)dependenciesMap.get(node.getDescriptor());
                if (n == null) {
                    n = new ArrayList();
                    dependenciesMap.put(node.getDescriptor(), n);
                }
                n.add(node);
            }
        }
        List list = sortModuleDescriptors(dependenciesMap.keySet());
        List ret = new ArrayList((int)(list.size()*1.3+nulls.size())); //attempt to adjust the size to avoid too much list resizing
        for (int i=0; i<list.size(); i++) {
            ModuleDescriptor md = (ModuleDescriptor)list.get(i);
            List n = (List)dependenciesMap.get(md);
            ret.addAll(n);            
        }
        ret.addAll(0, nulls);
        return ret;
    }


    /**
     * Sorts the given ModuleDescriptors from the less dependent to the more dependent.
     * This sort ensures that a ModuleDescriptor is always found in the list before all 
     * ModuleDescriptors depending directly on it.
     * @param moduleDescriptors a Collection of ModuleDescriptor to sort
     * @return a List of sorted ModuleDescriptors
     */
    public static List sortModuleDescriptors(Collection moduleDescriptors) {
        return new ModuleDescriptorSorter(moduleDescriptors).sortModuleDescriptors();   
    }
    
    
    private final Collection moduleDescriptors;
    private final Iterator moduleDescriptorsIterator;
    private final List sorted = new LinkedList();
    
    public ModuleDescriptorSorter(Collection moduleDescriptors) {
        this.moduleDescriptors=moduleDescriptors;
        moduleDescriptorsIterator = new LinkedList(moduleDescriptors).iterator();
    }
    
    /**
     * Iterates over all modules calling sortModuleDescriptorsHelp.
     * @return sorted module
     */
    public List sortModuleDescriptors() {
        while (moduleDescriptorsIterator.hasNext()) {
            sortModuleDescriptorsHelp((ModuleDescriptor)moduleDescriptorsIterator.next(), new Stack());
        }
        return sorted;
    }

    /**
     * If current module has already been added to list, returns,
     * Otherwise invokes sortModuleDescriptorsHelp for all dependencies
     * contained within set of moduleDescriptors.  Then finally adds self
     * to list of sorted.
     * @param current Current module to add to sorted list.
     */
    private void sortModuleDescriptorsHelp(ModuleDescriptor current, Stack callStack) {
        //if already sorted return
        if (sorted.contains(current)) {
            return;
        }
        DependencyDescriptor [] descriptors = current.getDependencies();
        ModuleDescriptor moduleDescriptorDependency = null;
        for (int i = 0; descriptors!=null && i < descriptors.length; i++) {
            moduleDescriptorDependency = getModuleDescriptorDependency(descriptors[i]);
            if (moduleDescriptorDependency != null && !callStack.contains(current)) {
                callStack.push(current);
                sortModuleDescriptorsHelp(moduleDescriptorDependency, callStack);
                callStack.pop();
            }
        }
        sorted.add(current);
    }

    /**
     * @param descriptor
     * @return a ModuleDescriptor from the collection of module descriptors to sort.
     * If none exists returns null.
     */
    private ModuleDescriptor getModuleDescriptorDependency(DependencyDescriptor descriptor) {
        Iterator i = moduleDescriptors.iterator();
        ModuleDescriptor md = null;
        while (i.hasNext()) {
            md = (ModuleDescriptor) i.next();
            if (descriptor.getDependencyId().equals(md.getModuleRevisionId().getModuleId())) {
                if (md.getResolvedModuleRevisionId().getRevision() == null) {
                    return md;
                } else if (descriptor.getDependencyRevisionId().acceptRevision(md.getResolvedModuleRevisionId().getRevision())) {
                    return md;
                }
            }
        }
        return null;
    }
}