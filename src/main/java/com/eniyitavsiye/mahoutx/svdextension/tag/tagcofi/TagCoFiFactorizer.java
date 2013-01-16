/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svdextension.tag.tagcofi;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.svd.AbstractFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.math.DiagonalMatrix;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.function.VectorFunction;

/**
 *
 * @author ceyhun
 */
public class TagCoFiFactorizer extends AbstractFactorizer {

	/**
	 * Number of factors;
	 */
	private int D;
	/**
	 * Number of iterations;
	 */
	private int W;
	/**
	 * Gradient descent step size.
	 */
	private double delta;
	/**
	 * Tag matrix.
	 */
	private Matrix tags;
	/**
	 * Similarity calculator.
	 */
	private SimilarityCalculator similarityCalculator;

	//hyperparameters?
	private double alpha;
	private double beta;

	private DataModel dataModel;

	public TagCoFiFactorizer(DataModel dataModel, Matrix tags, 
					SimilarityCalculator similarityCalculator, int D, int W, 
					double delta, double alpha, double beta) throws TasteException {
		super(dataModel);
		this.dataModel = dataModel;
		this.tags = tags;
		this.similarityCalculator = similarityCalculator;
		this.D = D;
		this.W = W;
		this.delta = delta;
		this.alpha = alpha;
		this.beta = beta;
	}

	@Override
	public Factorization factorize() throws TasteException {
		Matrix R = extractRatings();
		Matrix Z = computeTF_IDF(); //from tags
		Matrix S = similarityCalculator.calculateSimilarityFrom(Z);

		//D = diagonal matrix with column sums of S.
		//L = D - S (Laplacian)
		Matrix L = new DiagonalMatrix(S.aggregateColumns(new VectorFunction() {
			@Override
			public double apply(Vector f) {
				return f.zSum();
			}
		})).minus(S);

		final int M = R.rowSize(); //number of items
		final int N = R.columnSize(); // number of user.

		Matrix U = initialize(D, N);
		Matrix V = initialize(D, M);

		DiagonalMatrix alphaI = new DiagonalMatrix(alpha, M);

		//for each iteration
		for (int w = 1; w <= W; ++w) {

			//for each factor
			for (int d = 1; d <= D; ++d) {
				Matrix W = new DiagonalMatrix(computeVdjSquareSums(R, V));
				Vector x = computeX(R, U, V);
				//TODO move betaL to outside of loops
				Vector Ud = U.viewRow(d);
				Vector grad_f_Ud = W.plus(alphaI).plus(L.times(beta)).times(Ud).minus(x);
				Ud.assign(Ud.minus(grad_f_Ud.times(delta)));
			}
			//for each item
			for (int j = 1; j <= M; ++j) {
				Vector Vj = V.viewRow(j);
				Vector grad_f_Vj = (alphaI.plus(sumOuterUserFactorProducts(R, U))).times(Vj).minus(sumUserFactorWithJthItemRatings(R, U));
				Vj.assign(Vj.minus(grad_f_Vj.times(delta)));
			}
		
		}
		return createFactorization(extractDoubleArray(U), extractDoubleArray(V));
	}
	
	private double[][] extractDoubleArray(Matrix m) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private double[] computeVdjSquareSums(Matrix R, Matrix V) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private Matrix extractRatings() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private Matrix computeTF_IDF() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private Matrix initialize(int D, int N) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private Vector computeX(Matrix R, Matrix U, Matrix V) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private DiagonalMatrix sumOuterUserFactorProducts(Matrix R, Matrix U) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private Vector sumUserFactorWithJthItemRatings(Matrix R, Matrix U) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	public enum SimilarityCalculator {
		
		COSINE {
			@Override
			public Matrix calculateSimilarityFrom(Matrix m) {
				throw new UnsupportedOperationException("Not yet implemented");
			}
		},
		PEARSON {
			@Override
			public Matrix calculateSimilarityFrom(Matrix m) {
				throw new UnsupportedOperationException("Not yet implemented");
			}
		},
		EUCLIDEAN {
			@Override
			public Matrix calculateSimilarityFrom(Matrix m) {
				throw new UnsupportedOperationException("Not yet implemented");
			}
		};
		
		abstract Matrix calculateSimilarityFrom(Matrix m);

	}
	
}
