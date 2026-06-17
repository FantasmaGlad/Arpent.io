import { NextResponse } from 'next/server';
import { supabaseAdmin } from '@/lib/supabaseAdmin';

// Helper function to check if the caller is the authorized admin
async function verifyAdmin(request: Request) {
  const authHeader = request.headers.get('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return false;
  }
  const token = authHeader.split(' ')[1];
  const { data: { user }, error } = await supabaseAdmin.auth.getUser(token);
  
  if (error || !user) {
    return false;
  }
  
  const { data: adminRecord, error: adminError } = await supabaseAdmin
    .from('admins')
    .select('id')
    .eq('id', user.id)
    .single();

  return !adminError && adminRecord !== null;
}

export async function PUT(request: Request) {
  const isAdmin = await verifyAdmin(request);
  if (!isAdmin) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    const { 
      courseId, 
      est_bouclee, 
      distance_totale, 
      duree_secondes, 
      vitesse_moyenne, 
      vitesse_max, 
      allure_moyenne, 
      calories_estimees, 
      denivele_positif, 
      denivele_negatif, 
      points_gps_count 
    } = await request.json();

    if (!courseId) {
      return NextResponse.json({ error: 'Course ID is required' }, { status: 400 });
    }

    const updates: any = {};
    if (est_bouclee !== undefined) updates.est_bouclee = est_bouclee;
    if (distance_totale !== undefined) updates.distance_totale = distance_totale;
    if (duree_secondes !== undefined) updates.duree_secondes = duree_secondes;
    if (vitesse_moyenne !== undefined) updates.vitesse_moyenne = vitesse_moyenne;
    if (vitesse_max !== undefined) updates.vitesse_max = vitesse_max;
    if (allure_moyenne !== undefined) updates.allure_moyenne = allure_moyenne;
    if (calories_estimees !== undefined) updates.calories_estimees = calories_estimees;
    if (denivele_positif !== undefined) updates.denivele_positif = denivele_positif;
    if (denivele_negatif !== undefined) updates.denivele_negatif = denivele_negatif;
    if (points_gps_count !== undefined) updates.points_gps_count = points_gps_count;

    const { data, error } = await supabaseAdmin
      .from('courses')
      .update(updates)
      .eq('id', courseId)
      .select()
      .single();

    if (error) {
      return NextResponse.json({ error: error.message }, { status: 400 });
    }

    return NextResponse.json({ success: true, course: data });
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function DELETE(request: Request) {
  const isAdmin = await verifyAdmin(request);
  if (!isAdmin) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { searchParams } = new URL(request.url);
  const courseId = searchParams.get('courseId');

  if (!courseId) {
    return NextResponse.json({ error: 'Course ID is required' }, { status: 400 });
  }

  try {
    const { error } = await supabaseAdmin
      .from('courses')
      .delete()
      .eq('id', courseId);

    if (error) {
      return NextResponse.json({ error: error.message }, { status: 400 });
    }

    return NextResponse.json({ success: true });
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}
