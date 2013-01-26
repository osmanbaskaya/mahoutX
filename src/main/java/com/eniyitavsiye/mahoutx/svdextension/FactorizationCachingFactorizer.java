package com.eniyitavsiye.mahoutx.svdextension;

import java.util.Collection;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;

public class FactorizationCachingFactorizer implements Factorizer {

	private Factorization cachedFactorization;
	private Factorizer delegate;

	public Factorization getCachedFactorization() {
		return cachedFactorization;
	}
	
	public FactorizationCachingFactorizer(Factorizer delegate) {
		this.delegate = delegate;
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		delegate.refresh(alreadyRefreshed);
		/*
		try {
			cachedFactorization = delegate.factorize();
		} catch (TasteException e) {
		}
		*/
	}
	
	@Override
	public Factorization factorize() throws TasteException {
		cachedFactorization = delegate.factorize();
		return cachedFactorization;
	}

}
