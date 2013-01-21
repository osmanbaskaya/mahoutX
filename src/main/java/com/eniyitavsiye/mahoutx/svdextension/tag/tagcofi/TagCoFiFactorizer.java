/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svdextension.tag.tagcofi;

import java.util.Random;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.AbstractFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.DiagonalMatrix;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.SparseMatrix;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.function.DoubleDoubleFunction;
import org.apache.mahout.math.function.VectorFunction;

/**
 *
 * @author ceyhun
 */
public class TagCoFiFactorizer extends AbstractFactorizer {
	
	///home/ceyhun/Dropbox/Projects/doctoral/dataset/MovieLens/10M100K
	/**
	 * Dispersion parameter for gaussian in euclidean-based similarity.
	 */
	private static final double EUCLIDEAN_SIGMA = 0.5;

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
	 * Tag matrix of users.
	 */
	private Matrix userTagMatrix;
	/**
	 * Similarity calculator.
	 */
	private SimilarityCalculator similarityCalculator;

	//hyperparameters?
	private double alpha;
	private double beta;

	private DataModel dataModel;

	public TagCoFiFactorizer(DataModel dataModel, Matrix userTagMatrix, 
					SimilarityCalculator similarityCalculator, int D, int W, 
					double delta, double alpha, double beta) throws TasteException {
		super(dataModel);
		this.dataModel = dataModel;
		this.userTagMatrix = userTagMatrix;
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
		Matrix S = similarityCalculator.calculateSimilarityFrom(Z, userTagMatrix);

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

		Matrix U = initRandom(D, N, 0.1);
		Matrix V = initRandom(D, M, 0.1);

		DiagonalMatrix alphaI = new DiagonalMatrix(alpha, M);
		Matrix betaL = L.times(beta);

		//for each iteration
		for (int w = 1; w <= W; ++w) {

			//for each factor
			for (int d = 1; d <= D; ++d) {
				Matrix W = new DiagonalMatrix(computeVdjSquareSums(R, V, d));
				Vector x = computeXVector(R, U, V, d);
				Vector Ud = U.viewRow(d);
				Vector grad_f_Ud = W.plus(alphaI).plus(betaL).times(Ud).minus(x);
				Ud.assign(Ud.minus(grad_f_Ud.times(delta)));
			}
			//for each item
			for (int j = 1; j <= M; ++j) {
				Vector Vj = V.viewRow(j);
				Vector grad_f_Vj = (alphaI.plus(sumOuterUserFactorProducts(R, U, j))).times(Vj).minus(sumUserFactorWithJthItemRatings(R, U, j));
				Vj.assign(Vj.minus(grad_f_Vj.times(delta)));
			}
		
		}
		return createFactorization(extractDoubleArray(U), extractDoubleArray(V));
	}
	
	private double[][] extractDoubleArray(Matrix m) {
		double[][] arr = new double[m.numRows()][m.numCols()];
		for (int i = 0; i < arr.length; ++i) {
			for (int j = 0; j < arr[i].length; ++j) {
				arr[i][j] = m.get(i, j);
			}
		}
		return arr;
	}

	private double[] computeVdjSquareSums(Matrix R, Matrix V, int d) {
		int N = R.columnSize();
		double[] vec = new double[N];
		for (int i = 0; i < N; ++i) {
			Iterator<Element> nonZeroRatingsOfI = R.viewRow(i).iterateNonZero();
			while (nonZeroRatingsOfI.hasNext()) {
				int j = nonZeroRatingsOfI.next().index();
				double vdj = V.get(d, j);
				vec[i] += vdj*vdj;
			}
		}
		return vec;
	}

	private Matrix extractRatings() throws TasteException {
		int N = dataModel.getNumUsers();
		int M = dataModel.getNumItems();
		Matrix r = new SparseMatrix(N, M);
		LongPrimitiveIterator it = dataModel.getUserIDs();
		while (it.hasNext()) {
			long userID = it.nextLong();
			int u = userIndex(userID);
			PreferenceArray userPrefs = dataModel.getPreferencesFromUser(userID);
			for (long itemID : userPrefs.getIDs()) {
				int i = itemIndex(itemID);
				r.set(u, i, userPrefs.getValue(i));
			}
		}
		return r;
	}

	private Matrix computeTF_IDF() {
		int N = userTagMatrix.columnSize();
		int T = userTagMatrix.rowSize();
		//TODO SparseMatrix could be used instead.
		Matrix z = new DenseMatrix(N, T);
		for (int i = 0; i < N; ++i) {
			for (int j = 0; j < T; ++j) {
				double tf = userTagMatrix.get(i, j); //TODO calculate normalized frequency!
				double idf = userTagMatrix.viewColumn(j).zSum(); //TODO this may mean counting non-zeros.
				if (idf == 0) { //prevent NaN
					z.set(i, j, 0);
				} else {
					double tf_idf = tf * Math.log(N/idf)/Math.log(2);
					z.set(i, j, tf_idf);
				}
			}
		}
		return z;
	}

	private Matrix initRandom(int D, int N, double mag) {
		Random random = RandomUtils.getRandom();
		Matrix m = new DenseMatrix(D, N);
		for (int i = 0; i < D; ++i) {
			for (int j = 1; j < N; j++) {
				m.set(i, j, random.nextDouble() * mag - mag/2);
			}
		}
		return m;
	}

	private Vector computeXVector(Matrix R, Matrix U, Matrix V, int d) {
		int N = R.columnSize();
		Vector vec = new DenseVector(N);
		for (int i = 0; i < N; ++i) {
			double sum = 0;
			Iterator<Element> nonZeroRatingsOfI = R.viewRow(i).iterateNonZero();
			while (nonZeroRatingsOfI.hasNext()) {
				Element elem = nonZeroRatingsOfI.next();
				int j = elem.index();
				double rij = elem.get();
				double vdj = V.get(d, j);
				double uI_dot_vJ = U.viewColumn(i).dot(V.viewColumn(j));
				double udivdj = vdj * U.get(d, i);
				sum += vdj * (rij - uI_dot_vJ + udivdj);
			}
			vec.set(i, sum);
		}
		return vec;
	}

	private Matrix sumOuterUserFactorProducts(Matrix R, Matrix U, int j) {
		int N = U.rowSize();
		Matrix result = new DenseMatrix(N, N);
		Iterator<Element> nonZeroRatingsOfI = R.viewColumn(j).iterateNonZero();
		
		while (nonZeroRatingsOfI.hasNext()) {
			Element elem = nonZeroRatingsOfI.next();
			int i = elem.index();
			Vector uI = U.viewColumn(i);
			result.assign(result.plus(uI.cross(uI)));
		}
		return result;
	}

	private Vector sumUserFactorWithJthItemRatings(Matrix R, Matrix U, int j) {
		Vector vec = new DenseVector(D);
		Iterator<Element> nonZeroRatingsOfI = R.viewColumn(j).iterateNonZero();
		while (nonZeroRatingsOfI.hasNext()) {
			Element elem = nonZeroRatingsOfI.next();
			int i = elem.index();
			double rij = elem.get();
			vec.assign(vec.plus(U.viewColumn(i).times(rij)));
		}
		return vec;
	}

	public enum SimilarityCalculator {
		
		COSINE {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				int N = z.columnSize();
				Matrix s = new SparseMatrix(N, N);

				//TODO iterate over non-zero elements.
				for (int i = 0; i < N; ++i) {
					for (int j = i + 1; j < N; ++j) {

						Iterator<Element> commonTags = this.commonTagIterator(userTagMatrix, i, j);
						double dot = 0;
						double norm1 = 0;
						double norm2 = 0;
						while (commonTags.hasNext()) {
							int k = commonTags.next().index();
							double zik = z.get(i, k);
							double zij = z.get(i, j);
							dot += zik * zij;
							norm1 += zik * zik;
							norm2 += zij * zij;
						}
						double sim = dot / Math.sqrt(norm1 * norm2);
						if (isValidValue(sim)) {
							s.set(i, j, sim);
						}
					}
				}
				return s;
			}

		},
		PEARSON {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				int N = z.columnSize();
				Matrix s = new SparseMatrix(N, N);
				Vector zColMeans = z.aggregateColumns(new VectorFunction() {
					@Override
					public double apply(Vector f) {
						return f.zSum() / f.size();
					}
				});

				//TODO iterate over non-zero elements.
				for (int i = 0; i < N; ++i) {
					for (int j = i + 1; j < N; ++j) {

						Iterator<Element> commonTags = this.commonTagIterator(userTagMatrix, i, j);
						double dot = 0;
						double norm1 = 0;
						double norm2 = 0;
						while (commonTags.hasNext()) {
							int k = commonTags.next().index();
							double zik = z.get(i, k) - zColMeans.get(i);
							double zij = z.get(i, j) - zColMeans.get(j);
							dot += zik * zij;
							norm1 += zik * zik;
							norm2 += zij * zij;
						}
						double sim = dot / Math.sqrt(norm1 * norm2);
						sim = 1 / (1 + Math.exp(sim));
						
						if (isValidValue(sim)) {
							s.set(i, j, sim);
						}
					}
				}
				return s;
			}
		},
		EUCLIDEAN {
			@Override
			public Matrix calculateSimilarityFrom(Matrix z, Matrix userTagMatrix) {
				int N = z.columnSize();
				Matrix m = new SparseMatrix(N, N);
				Iterator<MatrixSlice> matIterator = m.iterateAll();
				while (matIterator.hasNext()) {
					MatrixSlice slice = matIterator.next();
					int i = slice.index();
					Iterator<Element> rowIterator = slice.vector().iterateNonZero();
					while (rowIterator.hasNext()) {
						Element elem = rowIterator.next();
						int j = elem.index();
						Iterator<Element> commonTags = commonTagIterator(userTagMatrix, i, j);
						double sim = 0;
						while (commonTags.hasNext()) {
							int k = commonTags.next().index();
							double zik = z.get(i, k);
							double zjk = z.get(j, k);
							double d = zik - zjk;
							sim += d * d;
						}
						sim = Math.exp(-sim / (2 * EUCLIDEAN_SIGMA * EUCLIDEAN_SIGMA));
						if (isValidValue(sim)) {
							m.set(i, j, sim);
						}
					}


				}
				return m;
			}
		};

		
		abstract Matrix calculateSimilarityFrom(Matrix Z, Matrix userTagMatrix);

		protected Iterator<Element> commonTagIterator(Matrix tag, int i, int j) {
			Vector tagsOfUserI = tag.viewRow(i);
			Vector tagsOfUserJ = tag.viewRow(j);

			Vector ijmtimes = tagsOfUserI.clone().assign(tagsOfUserJ, new DoubleDoubleFunction() {
					@Override
					public double apply(double arg1, double arg2) {
						return arg1 * arg2;
					}

				});
				Iterator<Element> commonTags = ijmtimes.iterateNonZero();
				return commonTags;
			}
	}
	
	private static boolean isValidValue(double sim) {
		return sim != 0 && !Double.isNaN(sim) && !Double.isInfinite(sim);
	}
}
