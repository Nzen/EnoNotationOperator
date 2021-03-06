/** see ../../../../../LICENSE for release details */
package ws.nzen.format.eno.parse;

import static ws.nzen.format.eno.EnoType.*;
import static ws.nzen.format.eno.parse.Syntaxeme.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import ws.nzen.format.eno.DocGen;
import ws.nzen.format.eno.Empty;
import ws.nzen.format.eno.EnoElement;
import ws.nzen.format.eno.EnoLocaleKey;
import ws.nzen.format.eno.EnoType;
import ws.nzen.format.eno.ExceptionStore;
import ws.nzen.format.eno.Field;
import ws.nzen.format.eno.FieldList;
import ws.nzen.format.eno.FieldSet;
import ws.nzen.format.eno.ListItem;
import ws.nzen.format.eno.Multiline;
import ws.nzen.format.eno.Section;
import ws.nzen.format.eno.SetEntry;
import ws.nzen.format.eno.Value;
import ws.nzen.format.eno.parse.Parser.Word;

/**  */
public class Grammarian
{
	private static final String cl = "s.";
	private List<List<Word>> parsedLines = new ArrayList<>();
	private List<Field> fields = new ArrayList<>();
	private List<Section> sections = new ArrayList<>();
	private List<Dependence> transitiveFields = new LinkedList<>();
	private List<Dependence> transitiveSections = new LinkedList<>();
	private int lineChecked = 0;
	private int wordIndOfLine = 0;

	private class Dependence
	{
		EnoElement hasReference = null;
		EnoElement isReferredTo = null;
		// minimum needed, in case this is a forward reference
		String nameOfReferredTo = "";
		int escapesOfReferredTo = 0;
	}


	public Grammarian()
	{
	}


	public Section analyze( List<String> fileLines )
	{
		fields.clear();
		transitiveFields.clear();
		sections.clear();	
		transitiveSections.clear();
		parsedLines = new Parser().parse( fileLines );
		// improve reset()
		Section entireResult = buildDocument();
		resolveForwardReferences();
		return entireResult;
	}


	private Section buildDocument()
	{
		String here = cl +"bd ";
		Section theDocument = new Section();
		EnoElement currElem = null;
		lineChecked = -1;
		int sectionDepth = 0;
		System.out.println( "\n"+ here +"starting at -1 ----" );
		int ind = 0;
		for (List<Word> line : parsedLines)
		{
			System.out.print( here +"doc L-"+ ind +", " );
			for (Word cw : line)
				System.out.print( " "+ cw.type );
			ind++;
			System.out.println();
		}
		while ( advanceLine() )
		{
			stdoutHistoryDebugger( here, "1", null, false );
			Syntaxeme focusType = peekAtNextLineType( -1 );
			stdoutHistoryDebugger( here, "2", focusType );
			String firstComment;
			Word currWord;
			if ( focusType == Syntaxeme.EMPTY )
			{
				break; // end of input; parser tells me how much trailing space to include
			}
			else if ( focusType == COMMENT )
			{
				stdoutHistoryDebugger( here, "3", null, false );
				currWord = popCurrentWordOfLine();
				stdoutHistoryDebugger( here, "4", currWord, true );
				if ( currWord == null )
					continue;
				else if ( currWord.type == Syntaxeme.EMPTY )
				{
					currWord = popCurrentWordOfLine(); // NOTE assuming well formed parser lines
					stdoutHistoryDebugger( here, "5", currWord, true );
					if ( currWord == null )
						continue; // assert paranoid
				}
				if ( currElem == null )
				{
					theDocument.addComment( currWord.value.trim() );
				}
				else
				{
					currElem.addComment( currWord.value.trim() );
				}
				continue;
			}
			firstComment = getPreceedingComment( true );
			stdoutHistoryDebugger( here, "5 post-gpc", null, false );
			currWord = peekAtCurrentWordOfLine();
			if ( currWord == null )
				continue;
			else if ( currWord.type == Syntaxeme.EMPTY )
			{
				stdoutHistoryDebugger( here, "6", currWord, true );
				wordIndOfLine += 1;
				currWord = peekAtCurrentWordOfLine();
					// NOTE assuming well formed parser lines
				stdoutHistoryDebugger( here, "7", currWord, true );
				if ( currWord == null )
					continue; // assert paranoid
				else
					wordIndOfLine -= 1; // NOTE reset word cursor;
					// Fix ASK why ? it'll point at empty again
				stdoutHistoryDebugger( here, "8", currWord, true );
			}
			switch ( currWord.type )
			{
				case SECTION :
				{
					stdoutHistoryDebugger( here, "9", currWord, false );
					currElem = section( firstComment, sectionDepth );
					stdoutHistoryDebugger( here, "10", currWord, false );
					break;
				}
				case FIELD :
				{
					stdoutHistoryDebugger( here, "11", currWord, true );
					currElem = field( firstComment );
					stdoutHistoryDebugger( here, "12", currWord, true );
					break;
				}
				case MULTILINE_BOUNDARY :
				{
					stdoutHistoryDebugger( here, "13", currWord, true );
					currElem = multiline( firstComment );
					stdoutHistoryDebugger( here, "14", currWord, true );
					break;
				}
				case BARE :
				{
					stdoutHistoryDebugger( here, "14a", currWord, true );
					currElem = new Empty( currWord.value, currWord.modifier );
					currElem.setLine( currWord.line );
					stdoutHistoryDebugger( here, "14b", currWord, true );
					break;
				}
				case VALUE :
				{
					complain(
							ExceptionStore.ANALYSIS,
							EnoLocaleKey.MISSING_ELEMENT_FOR_CONTINUATION,
							new Object[]{ currWord.line } );
				}
				case LIST_ELEMENT :
				{
					complain(
							ExceptionStore.ANALYSIS,
							EnoLocaleKey.MISSING_NAME_FOR_LIST_ITEM,
							new Object[]{ currWord.line } );
				}
				case SET_ELEMENT :
				{
					complain(
							ExceptionStore.ANALYSIS,
							EnoLocaleKey.MISSING_NAME_FOR_FIELDSET_ENTRY,
							new Object[]{ currWord.line } );
				}
				case MULTILINE_TEXT :
				case COPY :
				default :
				{
					// NOTE likely a Parser implementation problem, not user error
					complain(
							ExceptionStore.TOKENIZATION,
							EnoLocaleKey.INVALID_LINE,
							new Object[]{ currWord.line } );
				}
			}
			if ( currElem != null )
			{
				theDocument.addChild( currElem );
			}
		}
		return theDocument;
	}


	/** Save own info, save child elements, punt up
	 * when encounters a sibling or parent section. */
	private Section section( String firstComment, int parentDepth )
	{
		String here = cl +"s ";
		stdoutHistoryDebugger(here, "15", null, false);
		Word currWord = popCurrentWordOfLine();
		stdoutHistoryDebugger( here, "16", currWord, true );
		Word emptyLines = null;
		if ( currWord.type == null )
			throw new RuntimeException( "expected section" ); // assert paranoid
		else if ( currWord.type == Syntaxeme.EMPTY )
		{
			emptyLines = currWord;
			currWord = popCurrentWordOfLine();
			stdoutHistoryDebugger( here, "17", currWord, true );
		}
		Word sectionOperator;
		if ( currWord.type == null || currWord.type != Syntaxeme.SECTION )
			throw new RuntimeException( "expected section operator" ); // assert paranoid
		else
		{
			sectionOperator = currWord;
			currWord = popCurrentWordOfLine();
			stdoutHistoryDebugger( here, "18", currWord, true );
		}
		int ownDepth = sectionOperator.modifier;
		// NOTE checking if it's too deep
		if ( ownDepth != parentDepth +1 )
		{
			if ( ownDepth > parentDepth )
			{
				complain(
						ExceptionStore.ANALYSIS,
						EnoLocaleKey.SECTION_HIERARCHY_LAYER_SKIP,
						new Object[]{ currWord.line } );
			}
			else
			{
				// NOTE if a sibling or parent, let another level construct this section
				wordIndOfLine = 0;
				stdoutHistoryDebugger( here, "19", currWord, false );
				return null;
			}
		}
		if ( currWord.type == null || currWord.type != FIELD )
			throw new RuntimeException( "expected section name" ); // assert paranoid
		Section container = new Section( currWord.value, currWord.modifier );
		if ( emptyLines != null && emptyLines.modifier > 0 )
		{
			container.setPreceedingEmptyLines( emptyLines.modifier );
		}
		if ( ! firstComment.isEmpty() )
		{
			container.setFirstCommentPreceededName( true );
			container.addComment( firstComment );
		}
		container.setDepth( ownDepth );
		container.setLine( currWord.line );
		currWord = popCurrentWordOfLine();
		if ( currWord != null && currWord.type == COPY )
		{
			container.setShallowTemplate( currWord.modifier < 2 );
			currWord = popCurrentWordOfLine();
			stdoutHistoryDebugger( here, "21", currWord, true );
			if ( currWord.type == null || currWord.type != FIELD )
				throw new RuntimeException( "expected template name" ); // assert paranoid, parser should catch
			Dependence reference = new Dependence();
			reference.hasReference = container;
			reference.nameOfReferredTo = currWord.value;
			reference.escapesOfReferredTo = currWord.modifier;
			transitiveSections.add( reference );
		}
		sections.add( container );
		// ASK advanceLine();
		Syntaxeme nextType;
		EnoElement currChild = null;
		boolean addingChildren = true;
		while ( addingChildren )
		{
			nextType = peekAtNextLineType( 0 );
			stdoutHistoryDebugger( here, "22", nextType );
			switch ( nextType )
			{
				case EMPTY :
				{
					currChild = null;
					break;
				}
				case COMMENT :
				{
					advanceLine();
					stdoutHistoryDebugger( here, "23", currWord, false );
					currWord = popCurrentWordOfLine();
					stdoutHistoryDebugger( here, "24", currWord, true );
					if ( currWord.type == Syntaxeme.EMPTY )
					{
						// NOTE not keeping comments separated, else we'd need to save them as Value
						currWord = popCurrentWordOfLine();
						stdoutHistoryDebugger( here, "25", currWord, true );
					}
					if ( currWord.type == COMMENT )
					{
						container.addComment( currWord.value );
					}
					else
						throw new RuntimeException( "malformed parser line" );
					break;
				}
				case FIELD :
				{
					advanceLine();
					stdoutHistoryDebugger( here, "26", currWord, false );
					currChild = field( getPreceedingComment( true ) );
					stdoutHistoryDebugger( here, "27", currWord, false );
					break;
				}
				case MULTILINE_BOUNDARY :
				{
					advanceLine();
					stdoutHistoryDebugger( here, "28", currWord, false );
					currChild = multiline( getPreceedingComment( true ) );
					stdoutHistoryDebugger( here, "29",
							currWord, false );
					break;
				}
				case SECTION :
				{
					// NOTE ensuring we don't lose the preceeding comment
					int currLine = lineChecked;
					advanceLine();
					stdoutHistoryDebugger( here, "30",currWord, false );
					currChild = section(
							getPreceedingComment( true ), ownDepth );
					stdoutHistoryDebugger( here, "31", currWord, false );
					if ( currChild == null )
						lineChecked = currLine; // ASK vet that this is the right line to save
					break;
				}
				case BARE :
				{
					advanceLine();
					stdoutHistoryDebugger( here, "31a", currWord, true );
					currWord = popCurrentWordOfLine();
					if ( currWord.type == Syntaxeme.EMPTY )
					{
						currWord = popCurrentWordOfLine();
					}
					if ( currWord.type == Syntaxeme.BARE )
					{
						currChild = new Empty( currWord.value, currWord.modifier );
						currChild.setLine( currWord.line );
					}
					else
					{
						throw new RuntimeException( "malformed parser line" );
					}
					stdoutHistoryDebugger( here, "31c", currWord, true );
					break;
				}
				case VALUE :
				{
					complain(
							ExceptionStore.ANALYSIS,
							EnoLocaleKey.MISSING_ELEMENT_FOR_CONTINUATION,
							new Object[]{ currWord.line } );
				}
				case LIST_ELEMENT :
				{
					complain(
							ExceptionStore.ANALYSIS,
							EnoLocaleKey.MISSING_NAME_FOR_LIST_ITEM,
							new Object[]{ currWord.line } );
				}
				case SET_ELEMENT :
				{
					complain(
							ExceptionStore.ANALYSIS,
							EnoLocaleKey.MISSING_NAME_FOR_FIELDSET_ENTRY,
							new Object[]{ currWord.line } );
				}
				case MULTILINE_TEXT :
				case COPY :
				default :
				{
					complain(
							ExceptionStore.TOKENIZATION,
							EnoLocaleKey.INVALID_LINE,
							new Object[]{ currWord.line } );
				}
			}
			if ( currChild != null )
			{
				container.addChild( currChild );
			}
			else
			{
				addingChildren = false;
			}
		}
		return container;
	}


	/** Save own information and relevant children. */
	private Field field( String preceedingComment )
	{
		String here = cl +"f ";
		EnoType fieldType = FIELD_EMPTY;
		Word emptyLines = null;
		stdoutHistoryDebugger( here, "32", null, false );
		Word currWord = popCurrentWordOfLine();
		stdoutHistoryDebugger( here, "33", currWord, true );
		if ( currWord.type == Syntaxeme.EMPTY )
		{
			emptyLines = currWord;
			currWord = popCurrentWordOfLine();
			stdoutHistoryDebugger( here, "34", currWord, true );
		}
		if ( currWord.type != FIELD )
			throw new RuntimeException( "expected field name" ); // assert paranoid
		Word fieldName = currWord;
		Field emptySelf = new Field( fieldName.value, fieldName.modifier );
		emptySelf.setLine( fieldName.line );
		if ( ! preceedingComment.isEmpty() )
		{
			emptySelf.addComment( preceedingComment );
			emptySelf.setFirstCommentPreceededName( true );
		}
		if ( emptyLines != null )
		{
			emptySelf.setPreceedingEmptyLines( emptyLines.modifier );
		}
		Value lineSelf = null;
		FieldList listSelf = null;
		FieldSet pairedSelf = null;
		Dependence reference = null;
		currWord = popCurrentWordOfLine();
		stdoutHistoryDebugger( here, "35", currWord, true );
		if ( currWord != null )
		{
			// NOTE expecting value or template
			if ( currWord.type == VALUE )
			{
				lineSelf = new Value( emptySelf );
				lineSelf.append( currWord.value );
				fieldType = FIELD_VALUE;
			}
			else if ( currWord.type == COPY )
			{
				emptySelf.setShallowTemplate( currWord.modifier < 2 );
				currWord = popCurrentWordOfLine();
				stdoutHistoryDebugger( here, "36"
						, currWord, true );
				if ( currWord.type == null || currWord.type != FIELD )
					throw new RuntimeException( "expected template name" ); // assert paranoid
				reference = new Dependence();
				reference.hasReference = emptySelf;
				reference.nameOfReferredTo = currWord.value;
				reference.escapesOfReferredTo = currWord.modifier;
				transitiveFields.add( reference );
			}
			else
				throw new RuntimeException( "expected nothing, not "+ currWord.type ); // assert paranoid
		}
		Value currChild = null;
		String docComment = "";
		boolean nonChild = false; // NOTE encountered sibling field or parent section
		while ( true )
		{
			/*
			NP here I pull out the next line type, which is a preceeding
			comment, but I'm treating as a normal field comment.
			I'm concerned that if I pull out preceeding comment, it'll
			change the line cursor, especialy because I advance the
			line only if there isn't a doccomment. Figure out how to
			FIX it
			*/
			Syntaxeme nextType = peekAtNextLineType( 0 ); // NP here this was 1; differs now
			stdoutHistoryDebugger( here, "37", nextType );
			switch ( nextType )
			{
				case EMPTY :
				{
					nonChild = true;
					break;
				}
				case COMMENT :
				{
					advanceLine();
					stdoutHistoryDebugger( here, "38", currWord, true );
					currWord = popCurrentWordOfLine();
					stdoutHistoryDebugger( here, "39", currWord, true );
					if ( currWord.type == Syntaxeme.EMPTY )
					{
						// NOTE not keeping comments separated, else we'd need to save them as Value
						currWord = popCurrentWordOfLine();
						stdoutHistoryDebugger( here, "40", currWord, true );
					}
					if ( currWord.type == COMMENT )
					{
						if ( fieldType == FIELD_EMPTY )
						{
							emptySelf.addComment( currWord.value ); 
						}
						else if ( fieldType == FIELD_VALUE )
						{
							lineSelf.addComment( currWord.value );
						}
						else if ( fieldType == FIELD_SET
								|| fieldType == FIELD_LIST )
						{
							currChild.addComment( currWord.value );
						}
					}
					else
						throw new RuntimeException( "malformed parser line" );
					break;
				}
				case VALUE :
				{
					advanceLine();
					stdoutHistoryDebugger( here, "41", currWord, true );
					currWord = popCurrentWordOfLine();
					stdoutHistoryDebugger( here, "42", currWord, true );
					if ( currWord.type == Syntaxeme.EMPTY )
					{
						// NOTE not keeping value substrings separated
						currWord = popCurrentWordOfLine();
						stdoutHistoryDebugger( here, "43", currWord, true );
					}
					if ( currWord.type != VALUE )
					{
						throw new RuntimeException( "expected value" ); // assert paranoid
					}
					String continuation = ( currWord.modifier == Parser
							.WORD_MOD_CONT_EMPTY ) ? "" : " ";
					if ( fieldType == FIELD_EMPTY )
					{
						lineSelf = new Value( emptySelf );
						lineSelf.append( currWord.value );
						fieldType = FIELD_VALUE;
						if ( reference != null )
						{
							reference.hasReference = lineSelf;
						}
					}
					else if ( fieldType == FIELD_VALUE )
					{
						lineSelf.append( continuation + currWord.value );
					}
					else if ( fieldType == FIELD_LIST
							|| fieldType == FIELD_SET )
					{
						currChild.append( continuation + currWord.value );
					}
					break;
				}
				case LIST_ELEMENT :
				{
					docComment = getPreceedingComment();
					stdoutHistoryDebugger( here, "44"
							, currWord, true );
					if ( docComment.isEmpty() )
						advanceLine();
					stdoutHistoryDebugger( here, "45"
							, currWord, true );
					currWord = popCurrentWordOfLine();
					stdoutHistoryDebugger( here, "46"
							, currWord, true );
					if ( currWord.type == Syntaxeme.EMPTY )
					{
						emptyLines = currWord;
						currWord = popCurrentWordOfLine();
						stdoutHistoryDebugger( here, "47"
								, currWord, true );
					}
					if ( currWord.type != LIST_ELEMENT )
					{
						throw new RuntimeException( "expected list element" ); // assert paranoid
					}
					else if ( fieldType == FIELD_LIST || fieldType == FIELD_EMPTY )
					{
						if ( fieldType == FIELD_EMPTY )
						{
							fieldType = FIELD_LIST;
							listSelf = new FieldList( emptySelf );
							if ( reference != null )
							{
								reference.hasReference = listSelf;
							}
						}
						currChild = new ListItem( currWord.value );
						if ( ! docComment.isEmpty() )
						{
							currChild.addComment( docComment );
							currChild.setFirstCommentPreceededName( true );
						}
						if ( emptyLines != null && emptyLines.modifier != 0 )
						{
							currChild.setPreceedingEmptyLines( emptyLines.modifier );
							emptyLines.modifier = 0;
						}
						currChild.setLine( currWord.line );
						listSelf.addItem( (ListItem)currChild );
					}
					else if ( fieldType == FIELD_VALUE )
					{
						complain(
								ExceptionStore.ANALYSIS,
								EnoLocaleKey.LIST_ITEM_IN_FIELD,
								new Object[]{ currWord.line } );
					}
					else if ( fieldType == FIELD_SET )
					{
						complain(
								ExceptionStore.ANALYSIS,
								EnoLocaleKey.LIST_ITEM_IN_FIELDSET,
								new Object[]{ currWord.line } );
					}
					break;
				}
				case SET_ELEMENT :
				{
					docComment = getPreceedingComment();
					stdoutHistoryDebugger( here, "48"
							, currWord, true );
					if ( docComment.isEmpty() )
						advanceLine();
					stdoutHistoryDebugger( here, "49"
							, currWord, true );
					currWord = popCurrentWordOfLine();
					stdoutHistoryDebugger( here, "50"
							, currWord, true );
					if ( currWord.type == Syntaxeme.EMPTY )
					{
						emptyLines = currWord;
						currWord = popCurrentWordOfLine();
						stdoutHistoryDebugger( here, "51"
								, currWord, true );
					}
					else if ( fieldType == FIELD_SET || fieldType == FIELD_EMPTY )
					{
						if ( fieldType == FIELD_EMPTY )
						{
							fieldType = FIELD_SET;
							pairedSelf = new FieldSet( emptySelf );
							if ( reference != null )
							{
								reference.hasReference = pairedSelf;
							}
						}
						currChild = new SetEntry( currWord.value, currWord.modifier );
						currWord = popCurrentWordOfLine();
						stdoutHistoryDebugger( here, "52"
								, currWord, true );
						if ( currWord.type != VALUE )
							throw new RuntimeException( "expected set entry value token" ); // assert paranoid
						currChild.setStringValue( currWord.value );
						if ( ! docComment.isEmpty() )
						{
							currChild.addComment( docComment );
							currChild.setFirstCommentPreceededName( true );
						}
						if ( emptyLines != null && emptyLines.modifier != 0 )
						{
							currChild.setPreceedingEmptyLines( emptyLines.modifier );
							emptyLines.modifier = 0;
						}
						currChild.setLine( currWord.line );
						pairedSelf.addEntry( (SetEntry)currChild );
					}
					else if ( fieldType == FIELD_LIST )
					{
						complain(
								ExceptionStore.ANALYSIS,
								EnoLocaleKey.FIELDSET_ENTRY_IN_LIST,
								new Object[]{ currWord.line } );
					}
					else if ( fieldType == FIELD_VALUE )
					{
						complain(
								ExceptionStore.ANALYSIS,
								EnoLocaleKey.FIELDSET_ENTRY_IN_FIELD,
								new Object[]{ currWord.line } );
					}
					break;
				}
				default :
				{
					nonChild = true;
					break;
				}
			}
			if ( nonChild )
			{
				break;
			}
		}
		if ( fieldType == FIELD_LIST )
		{
			fields.add( listSelf );
			return listSelf;
		}
		else if ( fieldType == FIELD_VALUE )
		{
			fields.add( lineSelf );
			return lineSelf;
		}
		else if ( fieldType == FIELD_SET )
		{
			fields.add( pairedSelf );
			return pairedSelf;
		}
		else
		{
			fields.add( emptySelf );
			return emptySelf;
		}
	}


	private EnoElement multiline( String preceedingComment )
	{
		String here = cl +"ml ";
		Word emptyLines = null;
		stdoutHistoryDebugger( here, "53"
				, null, false );
		Word currWord = popCurrentWordOfLine();
		stdoutHistoryDebugger( here, "54"
				, currWord, true );
		if ( currWord.type == Syntaxeme.EMPTY )
		{
			emptyLines = currWord;
			currWord = popCurrentWordOfLine();
			stdoutHistoryDebugger( here, "55"
					, currWord, true );
		}
		if ( currWord.type != MULTILINE_BOUNDARY )
		{
			complain(
					ExceptionStore.TOKENIZATION,
					EnoLocaleKey.INVALID_LINE,
					new Object[]{ currWord.line } );
			// NOTE likely a Parser implementation problem, not user error
		}
		int boundaryHyphens = currWord.modifier;
		currWord = popCurrentWordOfLine();
		stdoutHistoryDebugger( here, "56"
				, currWord, true );
		if ( currWord.type != FIELD )
		{
			complain(
					ExceptionStore.TOKENIZATION,
					EnoLocaleKey.INVALID_LINE,
					new Object[]{ currWord.line } );
			// NOTE likely a Parser implementation problem, not user error
		}
		Multiline currElem = new Multiline( currWord.value, currWord.modifier );
		currElem.setBoundaryLength( boundaryHyphens );
		if ( emptyLines != null )
		{
			currElem.setPreceedingEmptyLines( emptyLines.modifier );
		}
		currWord = popCurrentWordOfLine();
		stdoutHistoryDebugger( here, "57"
				, currWord, true );
		if ( currWord.type != MULTILINE_TEXT )
		{
			complain(
					ExceptionStore.TOKENIZATION,
					EnoLocaleKey.INVALID_LINE,
					new Object[]{ currWord.line } );
			// NOTE likely a Parser implementation problem, not user error
		}
		currElem.setStringValue( ( currWord.value.isEmpty() )
				? null : currWord.value );
		// NOTE look for succeeding comments
		while ( peekAtNextLineType( 1 ) == COMMENT )
		{
			stdoutHistoryDebugger( here, "57"
					, currWord, true );
			advanceLine();
			stdoutHistoryDebugger( here, "58"
					, currWord, true );
			currWord = popCurrentWordOfLine();
			stdoutHistoryDebugger( here, "59"
					, currWord, true );
			if ( currWord.type == Syntaxeme.EMPTY )
			{
				currWord = popCurrentWordOfLine();
				stdoutHistoryDebugger( here, "60"
						, currWord, true );
			}
			if ( currWord.type != COMMENT )
				throw new RuntimeException( "expected comment" ); // assert paranoid
			else
				currElem.addComment( currWord.value );
		}
		fields.add( currElem );
		return currElem;
	}


	private String getPreceedingComment()
	{
		return getPreceedingComment( false );
	}


	/** Copy contiguous, immediately-preceding comments into a block
	 * with the minimum common whitespace, blank otherwise.
	 * Loses the preceeding empty line count. */
	private String getPreceedingComment( boolean startAtCurrentLine )
	{
		String here = cl +"gpc\t";
		List<String> comments = new ArrayList<>();
		boolean lineHasContent = false;
		int initialGlobalLineCursor = lineChecked;
		if ( startAtCurrentLine )
			lineChecked -= 1;
		Word currToken;
		stdoutHistoryDebugger( here, "61"
				, null, false );
		if ( advanceLine() )
		{
			stdoutHistoryDebugger( here, "62"
					, null, false );
			do
			{
				currToken = popCurrentWordOfLine();
				stdoutHistoryDebugger( here, "63"
						, currToken, true );
				if ( currToken == null )
					continue;
				else if ( currToken.type == Syntaxeme.EMPTY )
				{
					currToken = popCurrentWordOfLine();
					stdoutHistoryDebugger( here, "64"
							, currToken, true );
					if ( currToken == null )
						continue;
					// loop if paranoid, I'll assume we're well formed here
					else if ( currToken.type != Syntaxeme.EMPTY )
					{
						lineHasContent = true;
						break;
					}
				}
				else
				{
					lineHasContent = true;
					break;
				}
			}
			while ( advanceLine() );
			// NOTE either no document left or no comment
			if ( ! lineHasContent || currToken.type != COMMENT )
			{
				lineChecked = initialGlobalLineCursor;
				wordIndOfLine = 0; // ASK potentially save,restore ?
				stdoutHistoryDebugger( here, "65"
						, currToken, true );
				return "";
			}
			stdoutHistoryDebugger( here, "66"
					, currToken, true );
			boolean amAssociated = false;
			comments.add( currToken.value );
			while ( advanceLine() )
			{
				stdoutHistoryDebugger( here, "67"
						, currToken, true );
				currToken = popCurrentWordOfLine();
				stdoutHistoryDebugger( here, "68"
						, currToken, true );
				if ( currToken == null || currToken.type == Syntaxeme.EMPTY )
				{
					break;
				}
				else if ( currToken.type != COMMENT )
				{
					amAssociated = true;
					break;
				}
				else
				{
					comments.add( currToken.value );
				}
			}
			if ( ! amAssociated )
			{
				lineChecked = initialGlobalLineCursor;
				wordIndOfLine = 0; // ASK potentially save,restore ?
				stdoutHistoryDebugger( here, "69"
						, currToken, true );
				return "";
			}
			else
			{
				wordIndOfLine = 0; // NOTE reset because we popped rather than peeked
				Set<Character> whitespace = new TreeSet<>();
				whitespace.add( Character.valueOf( ' ' ) );
				whitespace.add( Character.valueOf( '\t' ) );
				NaiveTrie sequenceAware = new NaiveTrie( whitespace );
				Lexer reader = new Lexer();
				String commonPrefix = null;
				for ( String entire : comments )
				{
					reader.setLine( entire );
					Lexer.Token first = reader.nextToken();
					if ( first.type != Lexeme.WHITESPACE )
					{
						commonPrefix = "";
						break;
					}
					else
					{
						sequenceAware.add( first.word );
					}
				}
				if ( commonPrefix == null )
				{
					commonPrefix = sequenceAware.longestCommonPrefix();
				}
				StringBuilder wholeBlock = new StringBuilder( comments.size() * 10 );
				for ( String entire : comments )
				{
					wholeBlock.append( commonPrefix );
					wholeBlock.append( entire.trim() );
					wholeBlock.append( System.lineSeparator() );
				}
				stdoutHistoryDebugger( here, "70"
						, currToken, true );
				return wholeBlock.substring( 0, wholeBlock.length()
						- System.lineSeparator().length() ); // NOTE remove trailing \n
			}
		}
		else
		{
			lineChecked = initialGlobalLineCursor;
			wordIndOfLine = 0; // ASK potentially save,restore ?
			stdoutHistoryDebugger( here, "71"
					, null, false );
			return "";
		}
	}


	private Syntaxeme peekAtNextLineType( int offsetToNext )
	{
		String here = cl +"panlt\t";
		/*
		if line is empty, continue, nonstandard parser input
		if the first of it is empty, check next word
		if not comment, return that
		if comment iterate until a line starts with empty or noncomment
			if empty return comment, else return noncomment
		*/
		boolean vettingComment = false, firstTime = true;
		int nextLineInd = lineChecked + offsetToNext, wordInd = 0;
		Word currWord = null;
		List<Word> line = null;
		while ( true )
		{
			nextLineInd += 1;
			if ( nextLineInd >= parsedLines.size() )
			{
				if ( vettingComment )
				{
					return COMMENT;
				}
				else
				{
					return Syntaxeme.EMPTY;
				}
			}
			line = parsedLines.get( nextLineInd );
			if ( line.isEmpty() )
			{
				// warn about nonstandard parser line
				continue;
			}
			wordInd = 0;
			while ( wordInd < line.size() )
			{
				currWord = line.get( wordInd );
				if ( currWord.type == Syntaxeme.EMPTY )
				{
					if ( vettingComment )
						return Syntaxeme.COMMENT;
					else
						wordInd++;
				}
				else
				{
					break;
				}
			}
			if ( currWord.type == COMMENT
					&& firstTime )
			{
				vettingComment = true;
				firstTime = false;
			}
			else if ( wordInd < line.size() )
			{
				return currWord.type;
			}
			// NOTE else, check next line
		}
	}


	private void resolveForwardReferences()
	{
		findReferences( sections, transitiveSections );
		findReferences( fields, transitiveFields );
		/*
		validate that values aren't pointing at lists aren't pointing at sets
		fully check for cyclic dependendies (dijkstra ?)
		*/
	}


	/** Assumes caller provided elements of the right combination:
	 * provides minimal type checking. */
	private void findReferences(
			Collection<? extends EnoElement> concreteElements,
			Collection<Dependence> toResolve )
	{
		for ( Dependence ref : toResolve )
		{
			// FIX actually, below is not appropriate to reject as cyclic, if there's another
			// check for template < template
			if ( ref.hasReference.getName().equals( ref.nameOfReferredTo )
					&& ref.hasReference.getNameEscapes() == ref.escapesOfReferredTo )
			{
				complain(
						ExceptionStore.VALIDATION,
						EnoLocaleKey.CYCLIC_DEPENDENCY,
						new Object[]{ ref.hasReference.getName() } );
			}
			EnoElement target = null;
			for ( EnoElement candidate : concreteElements )
			{
				if ( ref.nameOfReferredTo.equals( candidate.getName() )
						&& ref.escapesOfReferredTo == candidate.getNameEscapes() )
				{
					// type matches or it's two field geneology (which != subclass)
					if ( target == null
							&& ( ref.hasReference.getType() == candidate.getType() )
								|| ( ref.hasReference.getType().templateAsField()
									&& candidate.getType().templateAsField() ) )
					{
						target = candidate;
					}
					else
					{
						complain(
								ExceptionStore.VALIDATION,
								EnoLocaleKey.MULTIPLE_TEMPLATES_FOUND,
								new Object[]{ ref.nameOfReferredTo } );
					}
				}
			}
			if ( target == null )
			{
				complain(
						ExceptionStore.VALIDATION,
						EnoLocaleKey.TEMPLATE_NOT_FOUND,
						new Object[]{ ref.nameOfReferredTo } );
			}
			else
			{
				ref.isReferredTo = target;
				affirmTemplateTypesAreCompatible(
						ref.hasReference.getType(),
						ref.isReferredTo.getType(),
						ref.hasReference.getName() );
				ref.hasReference.setTemplate( ref.isReferredTo );
			}
		}
		prohibitCycles( concreteElements, toResolve );
	}


	/**  */
	private void affirmTemplateTypesAreCompatible(
			EnoType hasReference, EnoType isReferredTo, String name )
	{
		if ( hasReference == isReferredTo )
		{
			return;
		}
		else if ( hasReference == EnoType.SECTION )
		{
			// assert not possible for a well behaved grammarian, given the field/section separation
			String key;
			switch ( isReferredTo )
			{
				case FIELD_EMPTY : { key = EnoLocaleKey.EXPECTED_SECTION_GOT_EMPTY; break; }
				case FIELD_GENERIC :
					{ key = EnoLocaleKey.EXPECTED_SECTION_GOT_FIELD; break; }
				case FIELD_VALUE :
				case MULTILINE :
					{ key = EnoLocaleKey.EXPECTED_SECTION_GOT_FIELD; break; }
				case FIELD_LIST :
				case LIST_ITEM :
				{ key = EnoLocaleKey.EXPECTED_SECTION_GOT_LIST; break; }
				case FIELD_SET :
				case SET_ELEMENT :
				{ key = EnoLocaleKey.EXPECTED_SECTION_GOT_FIELDSET; break; }
				default : { key = EnoLocaleKey.EXPECTED_ELEMENT_GOT_ELEMENTS; break; }
			}
			complain(
					ExceptionStore.VALIDATION,
					key,
					new Object[]{ name } );
		}
		// TODO continue for other complaints or allowances
	}


	/** Follows a chain of templates to null (vetted)
	 * or a previously seen name, at which point, it complains.
	 * Assumes that all forward references have been populated. */
	private void prohibitCycles(
			Collection<? extends EnoElement> concreteElements,
			Collection<Dependence> toResolve )
	{
		Map<Integer, String> escapeCache = new HashMap<>();
		escapeCache.put( Integer.valueOf( 0 ), "" );
		escapeCache.put( Integer.valueOf( 1 ),
				""+ Lexeme.ESCAPE_OP.getChar() );
		escapeCache.put( Integer.valueOf( 2 ),
				""+ Lexeme.ESCAPE_OP.getChar() + Lexeme.ESCAPE_OP.getChar() );
		Set<String> geneology = new HashSet<>();
		EnoElement referent = null;
		String escapes, fullName;
		int numEscapes = 0;
		for ( Dependence ref : toResolve )
		{
			geneology.clear();
			numEscapes = ref.hasReference.getNameEscapes();
			if ( ! escapeCache.containsKey( Integer.valueOf( numEscapes ) ) )
				escapes = DocGen.genEscapes( numEscapes );
			escapes = escapeCache.get( Integer.valueOf( numEscapes ) );
			fullName = ref.hasReference.getName() + escapes;
			geneology.add( fullName );
			referent = ref.hasReference.getTemplate();
			while ( referent != null )
			{
				numEscapes = ref.hasReference.getNameEscapes();
				if ( ! escapeCache.containsKey( Integer.valueOf( numEscapes ) ) )
					escapes = DocGen.genEscapes( numEscapes );
				escapes = escapeCache.get( Integer.valueOf( numEscapes ) );
				fullName = referent.getName();
				if ( ! geneology.contains( fullName ) )
				{
					geneology.add( fullName );
				}
				else
				{
					complain(
							ExceptionStore.VALIDATION,
							EnoLocaleKey.CYCLIC_DEPENDENCY,
							new Object[]{ ref.hasReference.getName() } );
				}
				referent = referent.getTemplate();
			}
		}
	}


	/** next word or null if none left. Advances wordIndOfLine. */
	private Word popCurrentWordOfLine()
	{
		if (  lineChecked < parsedLines.size() )
		{
			List<Word> line = parsedLines.get( lineChecked );
			if ( wordIndOfLine < line.size() )
			{
				Word result = line.get( wordIndOfLine );
				wordIndOfLine++;
				return result;
			}
			else
			{
				return null;
			}
		}
		else
		{
			return null;
		}
	}


	/** next word or null if none left. Advances wordIndOfLine. */
	private Word popNextWordOfLine()
	{
		if (  lineChecked < parsedLines.size() )
		{
			List<Word> line = parsedLines.get( lineChecked );
			wordIndOfLine++;
			if ( wordIndOfLine < line.size() )
			{
				return line.get( wordIndOfLine );
			}
			else
			{
				return null;
			}
		}
		else
		{
			return null;
		}
	}


	private Word peekAtCurrentWordOfLine()
	{
		if (  lineChecked < parsedLines.size() )
		{
			List<Word> line = parsedLines.get( lineChecked );
			if ( wordIndOfLine < line.size() )
			{
				return line.get( wordIndOfLine );
			}
			else
			{
				return null;
			}
		}
		else
		{
			return null;
		}
	}


	private Word peekAtNextWordOfLine()
	{
		if (  lineChecked < parsedLines.size() )
		{
			List<Word> line = parsedLines.get( lineChecked );
			if ( wordIndOfLine +1 < line.size() )
			{
				return line.get( wordIndOfLine +1 );
			}
			else
			{
				return null;
			}
		}
		else
		{
			return null;
		}
	}


	/** Increment line checked; reset word ind of line,
	 * report if this is out of bounds. */
	private boolean advanceLine()
	{
		if ( lineChecked < parsedLines.size() )
		{
			lineChecked++;
			wordIndOfLine = 0;
			return true;
		}
		else
		{
			return false;
		}
	}


	private void complain(
			String category,
			String key,
			Object[] values )
	{

		MessageFormat problem = new MessageFormat(
				ExceptionStore.getStore().getExceptionMessage(
						category, key ) );
		throw new RuntimeException( problem.format( values ) );
	}


	/** 4TESTS */
	private void stdoutHistoryDebugger( String here, String otherId,
			Syntaxeme typeFound )
	{
		System.out.println( here + otherId +" peek-type-"+ typeFound );
		stdoutHistoryDebugger(here, otherId, null, false);
	}


	/** 4TESTS */
	private void stdoutHistoryDebugger( String here, String otherId,
			Word currWord, boolean checkCurrWord )
	{
		if ( checkCurrWord )
		{
			if ( currWord != null )
			{
				System.out.println( here + otherId +" cw-type-"+ currWord.type );
			}
			else
			{
				System.out.println( here + otherId +" currWord is null" );
			}
		}
		if (lineChecked >= 0 && lineChecked < parsedLines.size()
				&& wordIndOfLine < parsedLines.get(lineChecked).size() )
		{
			System.out.println( here + otherId +" lc:"+ lineChecked +" wol:"
					+ wordIndOfLine +" type-"+ parsedLines.get(lineChecked)
					.get(wordIndOfLine).type );
		}
		else
		{
			System.out.println( here + otherId +" cursor overflow" );
		}
	}

}
























































